package com.yuankai.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.response.FavoriteArticleResponseDTO;
import com.yuankai.aispringboot.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 我的收藏分页查询：收藏记录联表文章表，一次取出列表展示所需的全部字段。
     * 过滤（user_id、文章已发布）与排序都下推到 SQL，不在应用层做全量捞出后过滤。
     */
    @Select("""
            SELECT f.id          AS favoriteId,
                   f.created_at  AS favoriteAt,
                   a.id          AS articleId,
                   a.title       AS title,
                   a.summary     AS summary,
                   a.cover_image AS cover,
                   a.tags        AS tags,
                   a.category_id AS categoryId,
                   a.read_count  AS readCount,
                   a.published_at AS publishAt,
                   u.username    AS authorName
            FROM user_favorite f
                     JOIN knowledge_article a ON a.id = f.article_id
                     LEFT JOIN `user` u ON u.id = a.author_id
            WHERE f.user_id = #{userId}
              AND a.status = 1
            ORDER BY f.created_at DESC
            """)
    Page<FavoriteArticleResponseDTO> selectFavoritePage(Page<FavoriteArticleResponseDTO> page,
                                                        @Param("userId") Long userId);
}
