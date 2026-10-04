package com.yuankai.aispringboot.DTO.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConsultationSessionQueryDTO {
    // 当前页码
    @NotNull(message = "当前页码不能为空")
    @Min(value = 1, message = "当前页码最小为1")
    private Integer currentPage;

    // 每页大小
    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 100, message = "每页大小最大为100")
    private Integer size;

    // 情绪标签（可选，用于筛选）
    private String emotionTag;

    // 按用户ID筛选（可选，仅管理员生效；普通用户固定查自己，该参数被忽略）
    @Min(value = 1, message = "用户ID最小为1")
    private Long userId;
}
