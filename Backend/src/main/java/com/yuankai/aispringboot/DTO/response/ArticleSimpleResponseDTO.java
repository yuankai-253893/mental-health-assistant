package com.yuankai.aispringboot.DTO.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder
@Data
public class ArticleSimpleResponseDTO {
    private String id;

    private String title;

    private Long categoryId;

    private String summary;

    // 封面图片（相对路径），用户端列表需要展示
    private String cover;

    private Integer readCount;

    private Integer status;

    private String tags;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime publishAt;

    // 作者名称
    private String authorName;
}
