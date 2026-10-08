package com.yuankai.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.query.OperationLogQueryDTO;
import com.yuankai.aispringboot.common.Result;
import com.yuankai.aispringboot.entity.OperationLog;
import com.yuankai.aispringboot.service.OperationLogService;
import com.yuankai.aispringboot.util.GetUserInfo;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询接口。
 *
 * 为什么不加 @OperationLog：日志的查询接口本身再写日志，会造成
 * 「管理员一刷新日志页就多一条日志」的自我放大，页面上永远多一条噪音记录。
 */
@Slf4j
@RestController
@RequestMapping("/api/operation-log")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    // 操作日志分页查询（仅管理员）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/page")
    public Result<Page<OperationLog>> getLogPage(@Valid OperationLogQueryDTO queryDTO) {
        Page<OperationLog> result = operationLogService.getLogPage(queryDTO);
        log.info("管理员{}查询操作日志", GetUserInfo.getUserId());
        return Result.success(result);
    }
}
