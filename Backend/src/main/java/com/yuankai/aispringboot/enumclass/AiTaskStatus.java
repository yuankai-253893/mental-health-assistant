package com.yuankai.aispringboot.enumclass;

import lombok.Getter;

/**
 * AI 分析任务状态机：PENDING → PROCESSING → COMPLETED / FAILED。
 * 与 ai_analysis_task.status（varchar）的取值一一对应。
 */
@Getter
public enum AiTaskStatus {

    PENDING("待处理"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String description;

    AiTaskStatus(String description) {
        this.description = description;
    }

    /** 任务是否处于「已结束」状态，用于判断能否重新入队 */
    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}
