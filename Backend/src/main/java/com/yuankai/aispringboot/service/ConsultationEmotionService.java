package com.yuankai.aispringboot.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuankai.aispringboot.AiService.PromptManage;
import com.yuankai.aispringboot.DTO.response.EmotionAnalysisResultDTO;
import com.yuankai.aispringboot.entity.ConsultationMessage;
import com.yuankai.aispringboot.entity.ConsultationSession;
import com.yuankai.aispringboot.mapper.ConsultationMessageMapper;
import com.yuankai.aispringboot.mapper.ConsultationSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 咨询会话情绪分析服务。
 *
 * 为什么放在流式对话之外异步执行：
 * 前端收到 SSE 的 done 事件才收起「AI 正在输入」并解锁输入框。若把情绪分析串在
 * sink.complete() 之前，一次大模型调用会让 done 延后数秒，用户会感到界面卡住。
 * 因此这里只负责「异步分析 + 写回」两步，前端按固定间隔回查结果，做到最终一致。
 *
 * 写入的两个字段：
 * - consultation_session.last_emotion_analysis / last_emotion_updated_at
 *   → 用户端「情绪花园」、后台咨询记录页与按情绪筛选都读它
 * - consultation_message.emotion_tag（最近一条用户消息）
 *   → 让消息级的情绪标签不再是永远为 null 的死字段
 */
@Slf4j
@Service
public class ConsultationEmotionService {

    /** 发送者类型：1-用户 */
    private static final int SENDER_USER = 1;

    /** 参与分析的最近用户消息条数：太少看不出情绪走向，太多只是浪费 token */
    private static final int MAX_CONTEXT_MESSAGES = 6;

    /** 模型漏填图标时的兜底值 */
    private static final String DEFAULT_ICON = "🙂";

    /** 日记分析的兜底分值：模型未给分时按中性处理 */
    private static final int DEFAULT_EMOTION_SCORE = 50;

    @Autowired
    @Qualifier("analysis-ai")
    private ChatClient analysisChatClient;

    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;

    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 异步分析指定会话的情绪并写回。
     */
    @Async("consultationEmotionExecutor")
    public void analyzeAndSaveAsync(Long sessionId) {
        try {
            analyzeAndSave(sessionId);
        } catch (Exception e) {
            // 情绪分析是对话的附加能力：失败只留日志，绝不能让分析失败影响已经完成的对话
            log.warn("会话{}情绪分析失败", sessionId, e);
        }
    }

    /**
     * 同步执行分析并写回，返回分析结果（无有效上下文时返回 null）。
     * 单独抽成 public 是为了便于直接调用与验证（异步入口只是一层线程派发）。
     */
    public EmotionAnalysisResultDTO analyzeAndSave(Long sessionId) {
        List<ConsultationMessage> context = loadRecentUserMessages(sessionId);
        if (context.isEmpty()) {
            return null;
        }

        EmotionAnalysisResultDTO result = analysisChatClient.prompt()
                .system(PromptManage.CONSULTATION_EMOTION_ANALYSIS_SYSTEM_PROMPT)
                .user(buildAnalysisPrompt(context))
                .call()
                .entity(EmotionAnalysisResultDTO.class);

        if (result == null || StrUtil.isBlank(result.getPrimaryEmotion())) {
            throw new IllegalStateException("AI 未返回有效的会话情绪分析结果");
        }
        fillDefaults(result);

        // 1) 写回会话：用户端「情绪花园」与后台咨询记录页读的就是这两个字段
        LambdaUpdateWrapper<ConsultationSession> sessionWrapper = new LambdaUpdateWrapper<>();
        sessionWrapper.eq(ConsultationSession::getId, sessionId)
                .set(ConsultationSession::getLastEmotionAnalysis, objectMapper.writeValueAsString(result))
                .set(ConsultationSession::getLastEmotionUpdatedAt, LocalDateTime.now());
        consultationSessionMapper.update(null, sessionWrapper);

        // 2) 回填最近一条用户消息的情绪标签
        ConsultationMessage latest = context.get(context.size() - 1);
        LambdaUpdateWrapper<ConsultationMessage> messageWrapper = new LambdaUpdateWrapper<>();
        messageWrapper.eq(ConsultationMessage::getId, latest.getId())
                .set(ConsultationMessage::getEmotionTag, result.getPrimaryEmotion());
        consultationMessageMapper.update(null, messageWrapper);

        log.info("会话{}情绪分析完成，主要情绪={}，风险等级={}",
                sessionId, result.getPrimaryEmotion(), result.getRiskLevel());
        return result;
    }

    /**
     * 取该会话最近若干条用户消息，并翻回时间正序。
     * 按 id 倒序而不是 created_at：同一秒内产生的消息 created_at 会打平，排序不稳定。
     */
    private List<ConsultationMessage> loadRecentUserMessages(Long sessionId) {
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId)
                .eq(ConsultationMessage::getSenderType, SENDER_USER)
                .orderByDesc(ConsultationMessage::getId)
                .last("LIMIT " + MAX_CONTEXT_MESSAGES);

        // selectList 返回的集合不保证可变，包一层再反转
        List<ConsultationMessage> messages = new ArrayList<>(consultationMessageMapper.selectList(queryWrapper));
        Collections.reverse(messages);
        return messages;
    }

    /** 组装发给模型的对话文本：只放用户发言，AI 回复对情绪判断价值低且会显著抬高 token */
    private String buildAnalysisPrompt(List<ConsultationMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ConsultationMessage message : messages) {
            builder.append("用户：").append(StrUtil.blankToDefault(message.getContent(), "")).append('\n');
        }
        return """
                请分析下面这段心理咨询对话中「用户」当前的整体情绪状态，并输出分析结果。

                对话记录：
                %s""".formatted(builder);
    }

    /** 服务端兜底补全，避免模型漏填导致前端渲染出空壳（与日记分析的处理保持一致） */
    private void fillDefaults(EmotionAnalysisResultDTO result) {
        result.setTimestamp(System.currentTimeMillis());
        if (result.getEmotionScore() == null) {
            result.setEmotionScore(DEFAULT_EMOTION_SCORE);
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
            result.setIcon(DEFAULT_ICON);
        }
    }
}
