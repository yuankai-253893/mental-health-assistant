package com.yuankai.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("user_favorite")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFavorite {
    // 收藏ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    @TableField("user_id")
    private Long userId;

    // 文章ID（knowledge_article.id，varchar(36) UUID）
    @TableField("article_id")
    private String articleId;

    // 收藏时间
    @TableField("created_at")
    private LocalDateTime createdAt;
}
