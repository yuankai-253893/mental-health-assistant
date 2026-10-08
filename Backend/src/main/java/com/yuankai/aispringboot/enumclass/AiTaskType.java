package com.yuankai.aispringboot.enumclass;

import lombok.Getter;

/**
 * AI 分析任务触发来源，对应 ai_analysis_task.task_type。
 */
@Getter
public enum AiTaskType {

    AUTO("自动触发"),      // 用户保存情绪日记时自动入队
    MANUAL("手动触发"),    // 用户在情绪日记页点击「重新分析」
    ADMIN("管理员触发"),   // 管理端对某条日记发起分析
    BATCH("批量触发");     // 批量补跑历史日记

    private final String description;

    AiTaskType(String description) {
        this.description = description;
    }
}
