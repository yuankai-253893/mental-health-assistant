package com.yuankai.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class ConsultationMessageResponseDTO {
    // 消息ID
    private Long id;

    // 会话ID
    private Long sessionId;

    // 发送者类型 1:用户 2:AI助手
    private Integer senderType;

    // 消息类型 1:文本
    private Integer messageType;

    // 消息内容
    private String content;

    // 情绪标签（由会话情绪分析异步回填，未分析时为空）
    private String emotionTag;

    // 使用的AI模型
    private String aiModel;

    // 创建时间
    private LocalDateTime createdAt;

}
