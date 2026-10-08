package com.yuankai.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端重置用户密码的请求体。
 * 字段名刻意不叫 password：OperationLog 切面的脱敏正则会同时屏蔽
 * password / confirmPassword / newPassword / oldPassword，
 * 保证明文密码不会被写进操作日志。
 */
@Data
public class UserPasswordUpdateDTO {
    // 新密码（长度约束与注册保持一致：6-50）
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度在6到50个字符之间")
    private String newPassword;
}
