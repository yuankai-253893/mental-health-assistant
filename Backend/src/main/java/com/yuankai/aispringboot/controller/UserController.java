package com.yuankai.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.command.UserLoginCommandDTO;
import com.yuankai.aispringboot.DTO.command.UserPasswordUpdateDTO;
import com.yuankai.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.yuankai.aispringboot.DTO.command.UserStatusUpdateDTO;
import com.yuankai.aispringboot.DTO.query.UserQueryDTO;
import com.yuankai.aispringboot.DTO.response.UserLoginResponseDTO;
import com.yuankai.aispringboot.annotation.OperationLog;
import com.yuankai.aispringboot.common.Result;
import com.yuankai.aispringboot.service.UserService;
import com.yuankai.aispringboot.util.GetUserInfo;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Resource
    private UserService userService;

    // 用户登录
    @OperationLog("用户登录")
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO) {  //登录引入Valid校验
        log.info("用户登录: {}", commandDTO.getUsername());
        // 调用服务层的登录方法
        UserLoginResponseDTO result = userService.login(commandDTO);

        return Result.success(result);
    }

    // 用户注册
    @OperationLog("用户注册")
    @PostMapping("/add")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(@Valid @RequestBody UserRegisterCommandDTO commandDTO) {
        UserLoginResponseDTO.UserDetailResponseDTO result = userService.register(commandDTO);
        return Result.success(result);
    }

    // 获取当前用户
    @GetMapping("/current")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser() {
        Long userId = GetUserInfo.getUserId();

        // 调用Service层获取用户详情
        UserLoginResponseDTO.UserDetailResponseDTO userDetail = userService.getUserById(userId);
        log.info("用户查询成功: {}", userId);

        return Result.success(userDetail);
    }

    // 用户退出登录
    @OperationLog("用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = GetUserInfo.getUserId();

        // 调用Service层将 Token 加入黑名单
        userService.logout(userId);
        log.info("用户 {} 已退出登录", userId);

        return Result.success();
    }

    // ==================== 管理端用户管理 ====================

    // 用户分页查询（支持按用户名、昵称、状态、用户类型筛选）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/page")
    public Result<Page<UserLoginResponseDTO.UserDetailResponseDTO>> getUserPage(@Valid UserQueryDTO queryDTO) {
        Page<UserLoginResponseDTO.UserDetailResponseDTO> result = userService.getUserPage(queryDTO);
        log.info("管理员{}查询用户列表", GetUserInfo.getUserId());
        return Result.success(result);
    }

    // 禁用 / 启用用户
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog("修改用户状态")
    @PutMapping("/admin/{id}/status")
    public Result<?> updateUserStatus(@Min(value = 1, message = "用户ID不合法") @PathVariable Long id,
                                      @Valid @RequestBody UserStatusUpdateDTO updateDTO) {
        userService.updateUserStatus(id, updateDTO.getStatus(), GetUserInfo.getUserId());
        return Result.success();
    }

    // 重置用户密码
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog("重置用户密码")
    @PutMapping("/admin/{id}/password")
    public Result<?> resetPassword(@Min(value = 1, message = "用户ID不合法") @PathVariable Long id,
                                   @Valid @RequestBody UserPasswordUpdateDTO updateDTO) {
        userService.resetPassword(id, updateDTO.getNewPassword());
        log.info("管理员{}重置用户{}密码", GetUserInfo.getUserId(), id);
        return Result.success();
    }

}
