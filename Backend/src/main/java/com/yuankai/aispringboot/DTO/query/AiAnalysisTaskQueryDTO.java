package com.yuankai.aispringboot.DTO.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiAnalysisTaskQueryDTO {
    // 当前页码
    @NotNull(message = "当前页码不能为空")
    @Min(value = 1, message = "当前页码最小为1")
    private Integer currentPage;

    // 每页大小
    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 100, message = "每页大小最大为100")
    private Integer size;

    // 任务状态筛选（可选）：PENDING（待处理） / PROCESSING（处理中） / COMPLETED（已完成） / FAILED（失败）
    private String status;

    // 按用户筛选（可选，管理端用）
    @Min(value = 1, message = "用户ID最小为1")
    private Long userId;

    // 按日记筛选（可选）
    @Min(value = 1, message = "日记ID最小为1")
    private Long diaryId;
}
