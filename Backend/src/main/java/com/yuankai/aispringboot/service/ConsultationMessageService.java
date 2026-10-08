package com.yuankai.aispringboot.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuankai.aispringboot.DTO.response.ConsultationMessagePageDTO;
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

    // 按会话分页查询消息（游标式翻页，向前翻历史）。
    //
    // 排序用 id 而不是 created_at：用户提问与 AI 回复常在同一秒内落库，按秒级时间戳会取错顺序。
    // id 自增且严格单调，天然就是「消息先后」的可靠游标。
    //
    // 游标语义：beforeId 为空 → 取最新一批；传入上一批的第一条消息 id → 取比它更早的那一批。
    // 返回前会把这一批反转成升序（最早在前），前端可以直接从头部插入而不必再排一次。
    //
    // 是否还有更早的消息：靠"多查 1 条"判定，而不是再发一条 COUNT。
    // 多查的这条既用来判断 hasMore，也顺带避免了 COUNT 与主查询之间的并发偏差（两次查询期间
    // 新插入的消息会让 COUNT 结果与列表对不上），代价只是一次多读一行。
    public ConsultationMessagePageDTO getMessagesBySessionId(Long sessionId, int limit, Long beforeId) {
        // 防御 limit：避免调用方传 0 / 负数导致 SQL 变成 "limit 0" 或负数而报错
        int safeLimit = Math.max(1, limit);

        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId);
        if (beforeId != null) {
            queryWrapper.lt(ConsultationMessage::getId, beforeId);
        }
        queryWrapper.orderByDesc(ConsultationMessage::getId)
                .last("limit " + (safeLimit + 1));

        List<ConsultationMessage> fetched =
                new ArrayList<>(consultationMessageMapper.selectList(queryWrapper));

        boolean hasMore = fetched.size() > safeLimit;
        if (hasMore) {
            // 多查出来的那条只用于判定，不返回给前端
            fetched.remove(fetched.size() - 1);
        }
        Collections.reverse(fetched);

        List<ConsultationMessageResponseDTO> records = fetched.stream()
                .map(this::convertToResponseDTO)
                .toList();
        return new ConsultationMessagePageDTO(records, hasMore);
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
