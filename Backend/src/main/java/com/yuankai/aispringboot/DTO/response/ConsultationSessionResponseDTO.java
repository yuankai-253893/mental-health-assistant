package com.yuankai.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConsultationSessionResponseDTO {
    // 会话ID
    private Long id;

    // 用户ID
    private Long userId;

    // 会话所属用户名（管理员查看全部会话时用于区分归属）
    private String username;

    // 会话标题
    private String sessionTitle;

    // 开始时间
    private LocalDateTime startedAt;

    // 最后一次情绪分析结果(JSON格式)
    private String lastEmotionAnalysis;

    // 最后一次情绪分析更新时间
    private LocalDateTime lastEmotionUpdatedAt;

    // 消息数量
    private Integer messageCount;

    // 最后一条消息内容（列表预览用）
    private String lastMessageContent;

    // 最后一条消息时间（列表展示用）
    private LocalDateTime lastMessageTime;
}
