package com.yuankai.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 我的收藏列表项：收藏记录 + 关联文章信息。
 *
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteArticleResponseDTO {
    // 收藏记录ID（取消收藏时使用）
    private Long favoriteId;

    // 收藏时间
    private LocalDateTime favoriteAt;

    // 文章ID
    private String articleId;

    private String title;

    private String summary;

    // 封面图片（相对路径）
    private String cover;

    // 标签，逗号分隔
    private String tags;

    private Long categoryId;

    private Integer readCount;

    private LocalDateTime publishAt;

    // 作者名称
    private String authorName;
}
