package com.yuankai.aispringboot.DTO.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 管理端操作日志分页查询条件。
 * 起止日期用 LocalDate 而不是 LocalDateTime：前端只需要选到「天」，
 * 结束日由服务端补到当天最后一刻，避免前端拼时间导致漏掉当天记录。
 */
@Data
public class OperationLogQueryDTO {
    // 当前页码
    @NotNull(message = "当前页码不能为空")
    @Min(value = 1, message = "当前页码最小为1")
    private Integer currentPage;

    // 每页大小
    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 100, message = "每页大小最大为100")
    private Integer size;

    // 操作用户名（可选，模糊匹配）
    private String username;

    // 操作描述（可选，模糊匹配）
    private String operation;

    // 执行结果（可选）0:失败 1:成功
    private Integer status;

    // 开始日期（可选，闭区间）
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    // 结束日期（可选，闭区间，服务端补到当天 23:59:59）
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
