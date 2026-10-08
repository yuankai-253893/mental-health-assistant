package com.yuankai.aispringboot.DTO;

import lombok.Data;

// 会话消息统计（会话列表预览用）
// 由 ConsultationMessageMapper.selectMessageStats 按 session_id 分组一次性查出，
// 用来替代「逐条会话各查一次消息数 + 各查一次最后一条消息」的 N+1 查询
@Data
public class SessionMessageStatDTO {
    // 会话ID
    private Long sessionId;

    // 该会话的消息总数
    private Integer messageCount;

    // 该会话最后一条消息的ID（再按 id 批量取回消息内容做列表预览）
    private Long lastMessageId;
}
