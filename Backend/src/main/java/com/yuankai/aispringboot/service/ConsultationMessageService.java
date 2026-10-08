package com.yuankai.aispringboot.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuankai.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.yuankai.aispringboot.entity.ConsultationMessage;
import com.yuankai.aispringboot.mapper.ConsultationMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ConsultationMessageService {
    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    public ConsultationMessage saveUserMessage (Long sessionId, String content, String emotionTag) {
        // 构建用户消息实体
        ConsultationMessage userMessage = ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(1)
                .messageType(1)
                .content(content)
                .emotionTag(emotionTag)
                .createdAt(LocalDateTime.now())
                .build();

        consultationMessageMapper.insert(userMessage);
        return userMessage;
    }

    public ConsultationMessage saveAiMessage(Long sessionId, String content, String aiModel) {
        ConsultationMessage aiMessage = ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(2)
                .messageType(1)
                .content(content)
                .aiModel(aiModel)
                .createdAt(LocalDateTime.now())
                .build();
        consultationMessageMapper.insert(aiMessage);
        return aiMessage;
    }

    public Integer getMessageCountBySessionId(Long sessionId) {
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId);

        Long count = consultationMessageMapper.selectCount(queryWrapper);
        return count.intValue();
    }

    // 根据会话ID获取最后一条消息，用于获取会话的最新状态
    // 按 id 倒序而不是 created_at：用户消息与 AI 回复常在同一秒内落库，按秒级时间戳会取错
    public ConsultationMessageResponseDTO getLastMessageBySessionId(Long sessionId) {
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId)
                .orderByDesc(ConsultationMessage::getId)
                .last("limit 1");

        ConsultationMessage lastMessage = consultationMessageMapper.selectOne(queryWrapper);
        return lastMessage != null ? convertToResponseDTO(lastMessage) : null;
    }

    // 根据会话ID获取消息列表，按时间升序返回，最多返回最近 limit 条
    // 先按 id 倒序取最近 limit 条再反转：会话消息可能很长，全量 selectList 会把整段对话读进内存；
    // 用 id 而不是 created_at 排序，避免同一秒内插入的多条消息顺序错乱
    public List<ConsultationMessageResponseDTO> getMessagesBySessionId(Long sessionId, int limit) {
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId)
                .orderByDesc(ConsultationMessage::getId)
                .last("limit " + limit);

        List<ConsultationMessage> messages =
                new ArrayList<>(consultationMessageMapper.selectList(queryWrapper));
        Collections.reverse(messages);
        return messages.stream().map(this::convertToResponseDTO).toList();
    }

    private ConsultationMessageResponseDTO convertToResponseDTO(ConsultationMessage message) {
        if (message == null) {
            return null;
        }

        // 手动逐字段赋值，确保转换的准确性和可控性
        ConsultationMessageResponseDTO responseDTO = new ConsultationMessageResponseDTO();
        responseDTO.setId(message.getId());
        responseDTO.setSessionId(message.getSessionId());
        responseDTO.setSenderType(message.getSenderType());
        responseDTO.setMessageType(message.getMessageType());
        responseDTO.setContent(message.getContent());
        responseDTO.setEmotionTag(message.getEmotionTag());
        responseDTO.setAiModel(message.getAiModel());
        responseDTO.setCreatedAt(message.getCreatedAt());

        return responseDTO;
    }
}
