package com.yuankai.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.query.FavoriteQueryDTO;
import com.yuankai.aispringboot.DTO.response.FavoriteArticleResponseDTO;
import com.yuankai.aispringboot.common.ResultCode;
import com.yuankai.aispringboot.entity.KnowledgeArticle;
import com.yuankai.aispringboot.entity.UserFavorite;
import com.yuankai.aispringboot.exception.BusinessException;
import com.yuankai.aispringboot.mapper.KnowledgeArticleMapper;
import com.yuankai.aispringboot.mapper.UserFavoriteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserFavoriteService {

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private KnowledgeArticleMapper knowledgeArticleMapper;

    /**
     * 收藏文章。设计为幂等接口：重复收藏不报错。
     * 并发下的重复提交由表上的唯一索引 user_article_unique(user_id, article_id) 兜底。
     */
    public void addFavorite(Long userId, String articleId) {
        if (knowledgeArticleMapper.selectById(articleId) == null) {
            throw new BusinessException(ResultCode.ARTICLE_NOT_FOUND.getCode(), ResultCode.ARTICLE_NOT_FOUND.getMsg());
        }

        // 已收藏直接返回，避免无意义的插入尝试
        if (exists(userId, articleId)) {
            return;
        }

        try {
            userFavoriteMapper.insert(UserFavorite.builder()
                    .userId(userId)
                    .articleId(articleId)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("用户{}收藏文章{}", userId, articleId);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：唯一索引拦截，语义等价于「已收藏」，不向用户报错
            log.debug("用户{}重复收藏文章{}，已由唯一索引拦截", userId, articleId);
        }
    }

    /** 取消收藏。同样是幂等接口：未收藏时直接返回成功。 */
    public void removeFavorite(Long userId, String articleId) {
        LambdaQueryWrapper<UserFavorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getArticleId, articleId);
        int deleted = userFavoriteMapper.delete(queryWrapper);
        if (deleted > 0) {
            log.info("用户{}取消收藏文章{}", userId, articleId);
        }
    }

    /** 是否已收藏：详情页与列表页用它回显星标状态 */
    public boolean isFavorited(Long userId, String articleId) {
        return exists(userId, articleId);
    }

    /** 我的收藏分页（列表展示字段由 SQL 联表一次取出） */
    public Page<FavoriteArticleResponseDTO> getMyFavorites(Long userId, FavoriteQueryDTO queryDTO) {
        Page<FavoriteArticleResponseDTO> page = new Page<>(queryDTO.getCurrentPage(), queryDTO.getSize());
        return userFavoriteMapper.selectFavoritePage(page, userId);
    }

    private boolean exists(Long userId, String articleId) {
        LambdaQueryWrapper<UserFavorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getArticleId, articleId);
        return userFavoriteMapper.exists(queryWrapper);
    }
}
