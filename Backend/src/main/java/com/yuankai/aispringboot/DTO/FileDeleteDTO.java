package com.yuankai.aispringboot.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除文件的请求体。
 *
 * 用「访问路径」而不是文件ID来定位文件：上传接口返回给前端的就只有 URL，
 * 前端拿不到文件ID（也不该为了删文件再查一次库），按 URL 删除才用得起来。
 */
@Data
public class FileDeleteDTO {
    // 文件访问路径，如 /files/bussiness/article/xxx.jpg
    @NotBlank(message = "文件路径不能为空")
    private String fileUrl;
}
