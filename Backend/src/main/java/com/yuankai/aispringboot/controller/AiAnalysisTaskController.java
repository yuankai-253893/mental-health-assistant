package com.yuankai.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.query.AiAnalysisTaskQueryDTO;
import com.yuankai.aispringboot.DTO.response.AiAnalysisTaskResponseDTO;
import com.yuankai.aispringboot.annotation.OperationLog;
import com.yuankai.aispringboot.common.Result;
import com.yuankai.aispringboot.entity.AiAnalysisTask;
import com.yuankai.aispringboot.enumclass.AiTaskType;
import com.yuankai.aispringboot.service.AiAnalysisTaskService;
import com.yuankai.aispringboot.util.GetUserInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Slf4j
@RestController
@RequestMapping("/api/ai-task")
public class AiAnalysisTaskController {

    @Autowired
    private AiAnalysisTaskService aiAnalysisTaskService;

    // ==================== 用户端 ====================

    /**
     * 为指定日记触发 AI 情绪分析（手动触发，高优先级）。
     * 先落任务再异步执行：executeTask 是 @Async，必须先等 createTask 的事务提交，
     * 否则异步线程查不到刚插入的任务记录。
     */
    @OperationLog("触发AI情绪分析")
    @PostMapping("/diary/{diaryId}")
    public Result<AiAnalysisTaskResponseDTO> analyzeDiary(
            @Min(value = 1, message = "日记ID不合法") @PathVariable Long diaryId) {
        Long userId = GetUserInfo.getUserId();

        AiAnalysisTask task = aiAnalysisTaskService.createTaskForCurrentUser(diaryId, userId, AiTaskType.MANUAL);
        aiAnalysisTaskService.executeTask(task.getId());

        log.info("用户{}触发日记{}的 AI 情绪分析，任务{}", userId, diaryId, task.getId());
        return Result.success(aiAnalysisTaskService.getTaskDetail(task.getId()));
    }

    /**
     * 查询某条日记最新的分析任务状态，前端据此轮询进度。
     * 任务不存在时返回 null（表示还没触发过分析），不是错误。
     */
    @GetMapping("/diary/{diaryId}")
    public Result<AiAnalysisTaskResponseDTO> getTaskByDiary(
            @Min(value = 1, message = "日记ID不合法") @PathVariable Long diaryId) {
        Long userId = GetUserInfo.getUserId();
        return Result.success(aiAnalysisTaskService.getLatestTaskByDiary(diaryId, userId));
    }

    // ==================== 管理端 ====================

    // 分析任务分页（可按状态、用户、日记筛选）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/page")
    public Result<Page<AiAnalysisTaskResponseDTO>> getTaskPage(@Valid AiAnalysisTaskQueryDTO queryDTO) {
        return Result.success(aiAnalysisTaskService.getTaskPage(queryDTO));
    }

    // 管理端为任意用户的日记发起分析
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog("管理端触发AI情绪分析")
    @PostMapping("/admin/diary/{diaryId}")
    public Result<AiAnalysisTaskResponseDTO> analyzeDiaryAsAdmin(
            @Min(value = 1, message = "日记ID不合法") @PathVariable Long diaryId) {
        AiAnalysisTask task = aiAnalysisTaskService.createTaskAsAdmin(diaryId, AiTaskType.ADMIN);
        aiAnalysisTaskService.executeTask(task.getId());
        log.info("管理员{}触发日记{}的 AI 情绪分析，任务{}", GetUserInfo.getUserId(), diaryId, task.getId());
        return Result.success(aiAnalysisTaskService.getTaskDetail(task.getId()));
    }

    // 重试失败的任务
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog("重试AI分析任务")
    @PostMapping("/admin/{taskId}/retry")
    public Result<?> retryTask(@Min(value = 1, message = "任务ID不合法") @PathVariable Long taskId) {
        aiAnalysisTaskService.retryTask(taskId);
        // 事务已在 retryTask 返回时提交，此处跨 Bean 调用才能让 @Async 生效
        aiAnalysisTaskService.executeTask(taskId);
        log.info("管理员{}重试 AI 分析任务{}", GetUserInfo.getUserId(), taskId);
        return Result.success();
    }
}
