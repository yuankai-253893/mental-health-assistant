package com.yuankai.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuankai.aispringboot.DTO.FileUploadDTO;
import com.yuankai.aispringboot.common.ResultCode;
import com.yuankai.aispringboot.exception.BusinessException;
import com.yuankai.aispringboot.entity.SysFileInfo;
import com.yuankai.aispringboot.mapper.SysFileInfoMapper;
import com.yuankai.aispringboot.util.ValidateMagicNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class SysFileInfoService {
    @Autowired
    private SysFileInfoMapper sysFileInfoMapper;

    @Value("${file.upload-path}")
    private String baseLocalPath;

    // 定义前端访问的基础路径
    private static final String BASE_URL_PATH = "/files/bussiness/";

    // 文件扩展名白名单（防止上传 .exe/.jsp/.html 等危险文件）
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf", "doc", "docx", "txt");

    public String upload(FileUploadDTO dto, Long userId) {
        MultipartFile file = dto.getFile();

        // 文件是否为空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_CONTENT_INVALID.getMsg());
        }

        // 2. 转换业务类型
        String realBusinessType = convertBusinessType(dto.getBusinessType());
        if (realBusinessType == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getMsg() + ": 不支持的业务类型");
        }

        // 3. 获取扩展名
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 4. 校验扩展名白名单
        String extLower = ext.replace(".", "").toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extLower)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED.getMsg() + ": " + ext + "，仅允许图片/PDF/文档");
        }

        // 4.1 校验文件头魔数（文件内容与扩展名必须一致，防 .exe 改名 .jpg 绕过白名单）
        try {
            ValidateMagicNumber.validateMagicNumber(file, extLower);
        } catch (IOException e) {
            log.error("读取文件头失败: {}", originalFilename, e);
            throw new BusinessException(ResultCode.FILE_CONTENT_INVALID.getMsg());
        }

        // 文件名使用 UUID 保证全局唯一：
        String timestampName = UUID.randomUUID().toString().replace("-", "") + ext;

        // 5. 拼接最终的本地物理路径和URL路径
        String relativePath = realBusinessType + "/" + timestampName;
        // baseLocalPath 末尾可能没有分隔符，做归一化，防止拼成 "uploaduser_avatar/..."
        String baseDir = baseLocalPath.endsWith("/") || baseLocalPath.endsWith("\\")
                ? baseLocalPath : baseLocalPath + File.separator;
        String localFilePath = baseDir + relativePath;
        String urlPath = BASE_URL_PATH + relativePath;

        // 6. 保存文件到本地
        try {
            File dest = new File(localFilePath);
            // 如果 user_avatar 目录不存在，先创建
            if (!dest.getParentFile().exists()) {
                boolean created = dest.getParentFile().mkdirs();
                if (!created) {
                    log.error("创建目录失败: {}", dest.getParentFile().getAbsolutePath());
                }
            }
            // 把文件写入磁盘
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("文件保存失败, path={}", localFilePath, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getMsg());
        }

        // 7. 构建实体并保存到数据库
        SysFileInfo sysFileInfo = SysFileInfo.builder()
                .originalName(originalFilename)
                .filePath(localFilePath)
                .fileSize(file.getSize())
                .fileType(ext.replace(".", "").toUpperCase())
                .businessType(realBusinessType.toUpperCase())
                .businessId(dto.getBusinessId())
                .businessField(dto.getBusinessField())
                .uploadUserId(userId)
                .isTemp(0)
                .status(1)
                .createTime(LocalDateTime.now())
                .expireTime(null)
                .build();

        sysFileInfoMapper.insert(sysFileInfo);

        return urlPath;
    }

    private String convertBusinessType(String businessType) {
        switch (businessType) {
            case "USER_AVATAR":
                return "user_avatar";
            case "ARTICLE":
                return "article";
            default:
                return null;
        }
    }

    // ==================== 删除 ====================

    /**
     * 按访问路径删除文件（带权限校验）。
     * 普通用户只能删自己上传的文件，管理员可删任意文件。
     *
     * 用访问路径而不是文件 ID 定位：上传接口返回给前端的只有 URL，
     * 前端拿不到 ID（也不该为了删文件再查一次库）。
     */
    public void deleteByUrl(String fileUrl, Long operatorId, boolean isAdmin) {
        String localFilePath = resolveLocalPath(fileUrl);
        SysFileInfo fileInfo = findActiveByLocalPath(localFilePath);

        if (fileInfo != null && !isAdmin && !Objects.equals(fileInfo.getUploadUserId(), operatorId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED.getCode(),
                    ResultCode.ACCESS_UNAUTHORIZED.getMsg());
        }
        // 表里查不到记录，说明该路径不是本系统上传的：无法核对归属，只允许管理员清理
        if (fileInfo == null && !isAdmin) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND.getCode(), ResultCode.FILE_NOT_FOUND.getMsg());
        }

        doDelete(localFilePath, fileInfo);
    }

    /**
     * 静默清理（供业务写路径调用，如文章换封面）。
     * 全程 try-catch：清理是收尾动作，绝不能因为删文件失败就让文章更新报错。
     */
    public void deleteByUrlQuietly(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            String localFilePath = resolveLocalPath(fileUrl);
            doDelete(localFilePath, findActiveByLocalPath(localFilePath));
        } catch (Exception e) {
            // 失败只会留下一个孤儿文件，不会破坏业务数据
            log.warn("清理文件失败，url={}", fileUrl, e);
        }
    }

    private SysFileInfo findActiveByLocalPath(String localFilePath) {
        LambdaQueryWrapper<SysFileInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysFileInfo::getFilePath, localFilePath)
                .eq(SysFileInfo::getStatus, 1)
                .orderByDesc(SysFileInfo::getId)
                .last("LIMIT 1");
        return sysFileInfoMapper.selectOne(queryWrapper);
    }

    /**
     * 把前端持有的访问路径换算成本地绝对路径，并做目录穿越防护 ——
     * 即使库里的路径被人为篡改，也只能删到上传目录内部的文件。
     */
    private String resolveLocalPath(String fileUrl) {
        String url = fileUrl == null ? "" : fileUrl.trim();
        if (!url.startsWith(BASE_URL_PATH)) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND.getCode(), "文件路径不合法");
        }
        String relativePath = url.substring(BASE_URL_PATH.length());
        String baseDir = baseLocalPath.endsWith("/") || baseLocalPath.endsWith("\\")
                ? baseLocalPath : baseLocalPath + File.separator;
        try {
            File base = new File(baseDir).getCanonicalFile();
            File target = new File(baseDir + relativePath).getCanonicalFile();
            if (!target.getPath().startsWith(base.getPath())) {
                throw new BusinessException(ResultCode.FILE_NOT_FOUND.getCode(), "文件路径不合法");
            }
            return target.getPath();
        } catch (IOException e) {
            log.warn("文件路径解析失败，url={}", fileUrl, e);
            throw new BusinessException(ResultCode.FILE_DELETE_FAILED.getCode(),
                    ResultCode.FILE_DELETE_FAILED.getMsg());
        }
    }

    private void doDelete(String localFilePath, SysFileInfo fileInfo) {
        // 1) 先删磁盘文件：删失败就抛异常、表记录保持「正常」，便于后续重试。
        //    反过来先软删记录的话，磁盘删失败就再没有机会补删了。
        File target = new File(localFilePath);
        if (target.exists() && !target.delete()) {
            throw new BusinessException(ResultCode.FILE_DELETE_FAILED.getCode(),
                    ResultCode.FILE_DELETE_FAILED.getMsg());
        }

        // 2) 再软删表记录（status=0）：保留行便于审计「谁在什么时候传过什么文件」
        if (fileInfo != null) {
            SysFileInfo update = new SysFileInfo();
            update.setId(fileInfo.getId());
            update.setStatus(0);
            sysFileInfoMapper.updateById(update);
        }

        log.info("文件已清理，path={}，表记录{}", localFilePath, fileInfo == null ? "不存在" : "已标记删除");
    }
}
