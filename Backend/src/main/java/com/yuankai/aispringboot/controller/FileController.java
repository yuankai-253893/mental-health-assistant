package com.yuankai.aispringboot.controller;

import com.yuankai.aispringboot.DTO.FileDeleteDTO;
import com.yuankai.aispringboot.DTO.FileUploadDTO;
import com.yuankai.aispringboot.annotation.OperationLog;
import com.yuankai.aispringboot.common.Result;
import com.yuankai.aispringboot.enumclass.UserType;
import com.yuankai.aispringboot.service.SysFileInfoService;
import com.yuankai.aispringboot.util.GetUserInfo;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件接口。
 * 类级路径为 /api/file、上传挂在 /upload 下，拼出来的地址与改造前
 * /api/file/upload 完全一致，前端上传无需改动；删除直接落在类级路径 /api/file 上。
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
public class FileController {
    @Autowired
    private SysFileInfoService sysFileInfoService;

    @OperationLog("文件上传")
    @PostMapping("/upload")
    public Result<String> uploadFile(@Valid FileUploadDTO fileUploadDTO) {
        Long userId = GetUserInfo.getUserId();
        // 调用 Service 层逻辑，返回文件访问路径
        String url = sysFileInfoService.upload(fileUploadDTO, userId);

        log.info("用户{}上传文件成功，文件访问路径{}", userId, url);
        return Result.success(url);
    }

    /**
     * 删除文件。
     * 普通用户只能删自己上传的文件，管理员可删任意文件（归属校验在 Service 层完成）。
     */
    @OperationLog("删除文件")
    @DeleteMapping
    public Result<?> deleteFile(@Valid @RequestBody FileDeleteDTO deleteDTO) {
        Long userId = GetUserInfo.getUserId();
        boolean isAdmin = UserType.ADMIN.getCode().equals(GetUserInfo.getUserType());

        sysFileInfoService.deleteByUrl(deleteDTO.getFileUrl(), userId, isAdmin);

        log.info("用户{}删除文件{}", userId, deleteDTO.getFileUrl());
        return Result.success();
    }
}
