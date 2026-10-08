package com.yuankai.aispringboot.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.AiService.PromptManage;
import com.yuankai.aispringboot.DTO.query.AiAnalysisTaskQueryDTO;
import com.yuankai.aispringboot.DTO.response.AiAnalysisTaskResponseDTO;
import com.yuankai.aispringboot.DTO.response.EmotionAnalysisResultDTO;
import com.yuankai.aispringboot.common.ResultCode;
import com.yuankai.aispringboot.entity.AiAnalysisTask;
import com.yuankai.aispringboot.entity.EmotionDiary;
import com.yuankai.aispringboot.enumclass.AiTaskStatus;
import com.yuankai.aispringboot.enumclass.AiTaskType;
import com.yuankai.aispringboot.exception.BusinessException;
import com.yuankai.aispringboot.mapper.AiAnalysisTaskMapper;
import com.yuankai.aispringboot.mapper.EmotionDiaryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 情绪分析任务服务。
 *
 * 为什么要有任务表：调用大模型是慢且会失败的外部依赖，直接放在「保存日记」的同步链路里，
 * 一旦模型超时用户就存不了日记。因此保存日记只负责入队，真正的分析由后台线程消费，
 * 失败可重试，并可通过任务表把执行过程、重试次数、失败原因全部留痕。
 */
@Slf4j
@Service
public class AiAnalysisTaskService {

    /** 默认优先级：2-正常（自动触发） */
    public static final int PRIORITY_NORMAL = 2;

    /** 高优先级：3-高（用户主动触发 / 管理员触发 / 手动重试） */
    public static final int PRIORITY_HIGH = 3;

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRY = 3;

    /** PROCESSING 超过该分钟数视为卡死，重新入队 */
    private static final int STUCK_TIMEOUT_MINUTES = 10;

    /** 错误信息落库前的截断长度 */
    private static final int MAX_ERROR_LENGTH = 500;

    @Autowired
    private AiAnalysisTaskMapper aiAnalysisTaskMapper;

    @Autowired
    private EmotionDiaryMapper emotionDiaryMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("analysis-ai")
    private ChatClient analysisChatClient;

    // ==================== 入队 ====================

    /** 当前用户为自己的日记触发分析 */
    @Transactional(rollbackFor = Exception.class)
    public AiAnalysisTask createTaskForCurrentUser(Long diaryId, Long userId, AiTaskType taskType) {
        EmotionDiary diary = requireOwnedDiary(diaryId, userId);
        return enqueue(diary, taskType, PRIORITY_HIGH);
    }

    /** 管理端为任意日记触发分析 */
    @Transactional(rollbackFor = Exception.class)
    public AiAnalysisTask createTaskAsAdmin(Long diaryId, AiTaskType taskType) {
        EmotionDiary diary = emotionDiaryMapper.selectById(diaryId);
        if (diary == null) {
            throw new BusinessException(ResultCode.EMOTIONDIARY_NOT_FOUND.getCode(),
                    ResultCode.EMOTIONDIARY_NOT_FOUND.getMsg());
        }
        return enqueue(diary, taskType, PRIORITY_HIGH);
    }

    /**
     * 保存日记后自动触发，只负责「入队」，返回任务ID（null 表示未入队）。
     *
     * 全程 try-catch：AI 分析是附加能力，绝不能因为它失败而让日记保存报错。
     */
    public Long triggerAutoAnalysis(Long diaryId, Long userId) {
        try {
            EmotionDiary diary = emotionDiaryMapper.selectById(diaryId);
            if (diary == null) {
                return null;
            }
            AiAnalysisTask task = enqueue(diary, AiTaskType.AUTO, PRIORITY_NORMAL);
            return task.getId();
        } catch (Exception e) {
            log.warn("日记{}自动触发 AI 分析失败", diaryId, e);
            return null;
        }
    }

    /** 入队：同一日记已有未结束的任务时直接复用，避免对同一条日记重复调用大模型 */
    private AiAnalysisTask enqueue(EmotionDiary diary, AiTaskType taskType, int priority) {
        AiAnalysisTask running = findRunningTask(diary.getId());
        if (running != null) {
            return running;
        }

        AiAnalysisTask task = AiAnalysisTask.builder()
                .diaryId(diary.getId())
                .userId(diary.getUserId())
                .status(AiTaskStatus.PENDING.name())
                .taskType(taskType.name())
                .priority(priority)
                .retryCount(0)
                .maxRetryCount(DEFAULT_MAX_RETRY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        aiAnalysisTaskMapper.insert(task);
        log.info("创建 AI 分析任务 id={}, diaryId={}, type={}", task.getId(), diary.getId(), taskType);
        return task;
    }

    // ==================== 执行 ====================

    /**
     * 异步执行单条任务。
     */
    @Async("aiTaskExecutor")
    public void executeTask(Long taskId) {
        // CAS 抢占：抢占失败说明已被其它消费者接管，直接退出，避免同一条任务被重复分析
        if (aiAnalysisTaskMapper.markProcessing(taskId) == 0) {
            return;
        }

        AiAnalysisTask task = aiAnalysisTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        try {
            EmotionDiary diary = emotionDiaryMapper.selectById(task.getDiaryId());
            if (diary == null) {
                markFailed(task, "日记不存在或已被删除");
                return;
            }

            EmotionAnalysisResultDTO result = analyze(diary);

            // 结果写回日记，前端「情绪花园」与情绪日记页读取该字段
            EmotionDiary diaryUpdate = new EmotionDiary();
            diaryUpdate.setId(diary.getId());
            diaryUpdate.setAiEmotionAnalysis(objectMapper.writeValueAsString(result));
            diaryUpdate.setAiAnalysisUpdatedAt(LocalDateTime.now());
            emotionDiaryMapper.updateById(diaryUpdate);

            markCompleted(task);
            log.info("AI 分析任务{}完成，日记{}，情绪={}", taskId, diary.getId(), result.getPrimaryEmotion());
        } catch (Exception e) {
            log.warn("AI 分析任务{}执行失败", taskId, e);
            markFailed(task, e.getMessage());
        }
    }

    /** 调用大模型做结构化情绪分析，返回值直接映射为 EmotionAnalysisResultDTO */
    private EmotionAnalysisResultDTO analyze(EmotionDiary diary) {
        EmotionAnalysisResultDTO result = analysisChatClient.prompt()
                .system(PromptManage.EMOTION_ANALYSIS_SYSTEM_PROMPT)
                .user(buildAnalysisPrompt(diary))
                .call()
                .entity(EmotionAnalysisResultDTO.class);

        if (result == null || StrUtil.isBlank(result.getPrimaryEmotion())) {
            throw new IllegalStateException("AI 未返回有效的情绪分析结果");
        }

        // 以下字段由服务端兜底补全，避免模型漏填导致前端渲染异常
        result.setTimestamp(System.currentTimeMillis());
        if (result.getEmotionScore() == null) {
            // 日记自评是 1-10，换算成 0-100 作为兜底分值
            result.setEmotionScore(diary.getMoodScore() == null ? 50 : diary.getMoodScore() * 10);
        }
        if (result.getIsNegative() == null) {
            result.setIsNegative(false);
        }
        if (result.getRiskLevel() == null) {
            result.setRiskLevel(0);
        }
        if (StrUtil.isBlank(result.getLabel())) {
            result.setLabel(result.getPrimaryEmotion());
        }
        if (StrUtil.isBlank(result.getIcon())) {
            result.setIcon("🙂");
        }
        return result;
    }

    private String buildAnalysisPrompt(EmotionDiary diary) {
        return """
                请分析下面这条情绪日记，并输出分析结果。

                日期：%s
                自评情绪分（1-10）：%s
                主要情绪：%s
                情绪触发因素：%s
                睡眠质量（1-5）：%s
                压力水平（1-5）：%s
                日记内容：%s
                """.formatted(
                diary.getDiaryDate(),
                diary.getMoodScore(),
                StrUtil.blankToDefault(diary.getDominantEmotion(), "未填写"),
                StrUtil.blankToDefault(diary.getEmotionTriggers(), "未填写"),
                diary.getSleepQuality(),
                diary.getStressLevel(),
                StrUtil.blankToDefault(diary.getDiaryContent(), "未填写"));
    }

    private void markCompleted(AiAnalysisTask task) {
        LambdaUpdateWrapper<AiAnalysisTask> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AiAnalysisTask::getId, task.getId())
                .set(AiAnalysisTask::getStatus, AiTaskStatus.COMPLETED.name())
                // 用 UpdateWrapper 才能真正写入 null；实体 updateById 默认忽略 null 字段
                .set(AiAnalysisTask::getErrorMessage, null)
                .set(AiAnalysisTask::getCompletedAt, LocalDateTime.now())
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now());
        aiAnalysisTaskMapper.update(null, updateWrapper);
    }

    /**
     * 标记失败：未超过重试上限则退回 PENDING 等待下一轮调度，
     * 超过上限才置为终态 FAILED —— 避免坏数据无限重试打爆大模型配额。
     */
    private void markFailed(AiAnalysisTask task, String error) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetryCount() == null ? DEFAULT_MAX_RETRY : task.getMaxRetryCount();
        boolean canRetry = retryCount < maxRetry;

        LambdaUpdateWrapper<AiAnalysisTask> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AiAnalysisTask::getId, task.getId())
                .set(AiAnalysisTask::getStatus, canRetry ? AiTaskStatus.PENDING.name() : AiTaskStatus.FAILED.name())
                .set(AiAnalysisTask::getRetryCount, retryCount + 1)
                .set(AiAnalysisTask::getErrorMessage,
                        StrUtil.maxLength(StrUtil.blankToDefault(error, "未知错误"), MAX_ERROR_LENGTH))
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now());
        if (!canRetry) {
            updateWrapper.set(AiAnalysisTask::getCompletedAt, LocalDateTime.now());
        }
        aiAnalysisTaskMapper.update(null, updateWrapper);

        log.info("AI 分析任务{}失败（第 {}/{} 次），{}", task.getId(), retryCount + 1, maxRetry,
                canRetry ? "已重新入队" : "已达重试上限");
    }

    // ==================== 重试与调度 ====================

    /**
     * 管理端手动重试：仅 FAILED 任务可重试，并重置重试计数与优先级。
     * 这里只负责「重新入队」，真正的执行由调用方通过 executeTask 派发：
     * 本方法是 @Transactional，若在此处 this.executeTask(...) 属于同类自调用，
     * @Async 不会生效（不走代理），会把一次大模型调用同步压在管理端的 HTTP 请求里。
     */
    @Transactional(rollbackFor = Exception.class)
    public void retryTask(Long taskId) {
        AiAnalysisTask task = aiAnalysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.ANALYSIS_TASK_NOT_FOUND.getCode(),
                    ResultCode.ANALYSIS_TASK_NOT_FOUND.getMsg());
        }
        if (!AiTaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new BusinessException(ResultCode.ANALYSIS_TASK_NOT_RETRYABLE.getCode(),
                    ResultCode.ANALYSIS_TASK_NOT_RETRYABLE.getMsg());
        }

        LambdaUpdateWrapper<AiAnalysisTask> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AiAnalysisTask::getId, taskId)
                .set(AiAnalysisTask::getStatus, AiTaskStatus.PENDING.name())
                .set(AiAnalysisTask::getPriority, PRIORITY_HIGH)
                .set(AiAnalysisTask::getRetryCount, 0)
                .set(AiAnalysisTask::getErrorMessage, null)
                .set(AiAnalysisTask::getStartedAt, null)
                .set(AiAnalysisTask::getCompletedAt, null)
                .set(AiAnalysisTask::getUpdatedAt, LocalDateTime.now());
        aiAnalysisTaskMapper.update(null, updateWrapper);
    }

    /** 调度器调用：取一批待处理任务 */
    public List<AiAnalysisTask> takePendingTasks(int limit) {
        return aiAnalysisTaskMapper.selectPendingTasks(limit);
    }

    /** 调度器调用：回收卡死的 PROCESSING 任务，返回回收条数 */
    public int requeueStuckTasks() {
        return aiAnalysisTaskMapper.requeueStuckTasks(STUCK_TIMEOUT_MINUTES);
    }

    // ==================== 查询 ====================

    /** 按任务ID查详情，用于触发分析后立即回显任务状态 */
    public AiAnalysisTaskResponseDTO getTaskDetail(Long taskId) {
        AiAnalysisTask task = aiAnalysisTaskMapper.selectById(taskId);
        return task == null ? null : toResponseDTO(task);
    }

    /** 查询某条日记当前的分析任务（前端轮询进度用），仅限本人 */
    public AiAnalysisTaskResponseDTO getLatestTaskByDiary(Long diaryId, Long userId) {
        requireOwnedDiary(diaryId, userId);
        AiAnalysisTask task = findLatestTask(diaryId);
        return task == null ? null : toResponseDTO(task);
    }

    /** 管理端任务分页 */
    public Page<AiAnalysisTaskResponseDTO> getTaskPage(AiAnalysisTaskQueryDTO queryDTO) {
        Page<AiAnalysisTaskResponseDTO> page = new Page<>(queryDTO.getCurrentPage(), queryDTO.getSize());
        Page<AiAnalysisTaskResponseDTO> result = aiAnalysisTaskMapper.selectTaskPage(
                page, queryDTO.getStatus(), queryDTO.getUserId(), queryDTO.getDiaryId());
        result.getRecords().forEach(dto -> dto.setStatusText(statusText(dto.getStatus())));
        return result;
    }

    // ==================== 内部工具 ====================

    /** 校验日记存在且属于当前用户（防水平越权：不能分析别人的日记） */
    private EmotionDiary requireOwnedDiary(Long diaryId, Long userId) {
        EmotionDiary diary = emotionDiaryMapper.selectById(diaryId);
        if (diary == null) {
            throw new BusinessException(ResultCode.EMOTIONDIARY_NOT_FOUND.getCode(),
                    ResultCode.EMOTIONDIARY_NOT_FOUND.getMsg());
        }
        if (!diary.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED.getCode(),
                    ResultCode.ACCESS_UNAUTHORIZED.getMsg());
        }
        return diary;
    }

    private AiAnalysisTask findRunningTask(Long diaryId) {
        LambdaQueryWrapper<AiAnalysisTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiAnalysisTask::getDiaryId, diaryId)
                .in(AiAnalysisTask::getStatus, AiTaskStatus.PENDING.name(), AiTaskStatus.PROCESSING.name())
                .orderByDesc(AiAnalysisTask::getId)
                .last("LIMIT 1");
        return aiAnalysisTaskMapper.selectOne(queryWrapper);
    }

    private AiAnalysisTask findLatestTask(Long diaryId) {
        LambdaQueryWrapper<AiAnalysisTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiAnalysisTask::getDiaryId, diaryId)
                .orderByDesc(AiAnalysisTask::getId)
                .last("LIMIT 1");
        return aiAnalysisTaskMapper.selectOne(queryWrapper);
    }

    private AiAnalysisTaskResponseDTO toResponseDTO(AiAnalysisTask task) {
        return AiAnalysisTaskResponseDTO.builder()
                .id(task.getId())
                .diaryId(task.getDiaryId())
                .userId(task.getUserId())
                .status(task.getStatus())
                .statusText(statusText(task.getStatus()))
                .taskType(task.getTaskType())
                .priority(task.getPriority())
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .errorMessage(task.getErrorMessage())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private String statusText(String status) {
        if (StrUtil.isBlank(status)) {
            return "";
        }
        try {
            return AiTaskStatus.valueOf(status).getDescription();
        } catch (IllegalArgumentException e) {
            // 库中可能存在历史遗留的未知状态，原样返回而不是抛异常打断整个列表查询
            return status;
        }
    }
}
