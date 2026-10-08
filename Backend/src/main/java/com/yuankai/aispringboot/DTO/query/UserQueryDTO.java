package com.yuankai.aispringboot.DTO.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端用户分页查询条件。
 * 所有筛选项都是可选的，不传即不参与 WHERE 拼接。
 */
@Data
public class UserQueryDTO {
    // 当前页码
    @NotNull(message = "当前页码不能为空")
    @Min(value = 1, message = "当前页码最小为1")
    private Integer currentPage;

    // 每页大小
    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 100, message = "每页大小最大为100")
    private Integer size;

    // 用户名（可选，模糊匹配）
    private String username;

    // 昵称（可选，模糊匹配）
    private String nickname;

    // 状态（可选）0:禁用 1:正常
    private Integer status;

    // 用户类型（可选）1:普通用户 2:管理员
    private Integer userType;
}
