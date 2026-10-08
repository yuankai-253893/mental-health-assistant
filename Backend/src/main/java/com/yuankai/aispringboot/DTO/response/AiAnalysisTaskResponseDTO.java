package com.yuankai.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 分析任务返回结构。
 * 前端据此判断到底是「排队中 / 处理中 / 已完成 / 失败」，并决定轮询还是展示结果。
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisTaskResponseDTO {
    private Long id;

    private Long diaryId;

    private Long userId;

    // 所属用户名（管理端列表用于区分归属）
    private String username;

    // 任务状态：PENDING（待处理） / PROCESSING（处理中） / COMPLETED（已完成） / FAILED（失败）
    private String status;

    // 状态中文描述，前端直接展示
    private String statusText;

    // 触发来源：AUTO（自动触发） / MANUAL（手动触发） / ADMIN（管理员触发） / BATCH（批量触发）
    private String taskType;

    private Integer priority;

    private Integer retryCount;

    private Integer maxRetryCount;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
}
