package com.yuankai.aispringboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端修改用户状态（禁用 / 启用）的请求体。
 */
@Data
public class UserStatusUpdateDTO {
    // 状态 0:禁用 1:正常
    @NotNull(message = "用户状态不能为空")
    @Min(value = 0, message = "用户状态只能是 0(禁用) 或 1(正常)")
    @Max(value = 1, message = "用户状态只能是 0(禁用) 或 1(正常)")
    private Integer status;
}
