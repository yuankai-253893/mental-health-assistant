package com.yuankai.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 情绪分析任务（异步队列）。
 * 情绪分析要调用大模型，耗时且可能失败，因此不放在保存日记的同步链路里，
 * 而是落一条任务记录后由后台线程消费，失败可重试。
 */
@Data
@TableName("ai_analysis_task")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisTask {
    // 任务ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 待分析的日记ID
    @TableField("diary_id")
    private Long diaryId;

    // 任务所属用户ID（冗余存储，便于按用户过滤与权限校验）
    @TableField("user_id")
    private Long userId;

    // 任务状态：PENDING / PROCESSING / COMPLETED / FAILED
    @TableField("status")
    private String status;

    // 触发来源：AUTO / MANUAL / ADMIN / BATCH
    @TableField("task_type")
    private String taskType;

    // 优先级：1-低，2-正常，3-高，4-紧急
    @TableField("priority")
    private Integer priority;

    // 已重试次数
    @TableField("retry_count")
    private Integer retryCount;

    // 最大重试次数
    @TableField("max_retry_count")
    private Integer maxRetryCount;

    // 失败原因
    @TableField("error_message")
    private String errorMessage;

    // 开始处理时间
    @TableField("started_at")
    private LocalDateTime startedAt;

    // 处理完成时间
    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
