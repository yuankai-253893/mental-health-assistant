package com.yuankai.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.query.FavoriteQueryDTO;
import com.yuankai.aispringboot.DTO.response.FavoriteArticleResponseDTO;
import com.yuankai.aispringboot.annotation.OperationLog;
import com.yuankai.aispringboot.common.Result;
import com.yuankai.aispringboot.service.UserFavoriteService;
import com.yuankai.aispringboot.util.GetUserInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Slf4j
@RestController
@RequestMapping("/api/favorite")
public class UserFavoriteController {

    @Autowired
    private UserFavoriteService userFavoriteService;

    // 收藏文章（幂等，重复收藏不报错）
    @OperationLog("收藏文章")
    @PostMapping("/{articleId}")
    public Result<?> addFavorite(@NotBlank(message = "文章ID不能为空") @PathVariable String articleId) {
        Long userId = GetUserInfo.getUserId();
        userFavoriteService.addFavorite(userId, articleId);
        return Result.success();
    }

    // 取消收藏（幂等，未收藏时也返回成功）
    @OperationLog("取消收藏")
    @DeleteMapping("/{articleId}")
    public Result<?> removeFavorite(@NotBlank(message = "文章ID不能为空") @PathVariable String articleId) {
        Long userId = GetUserInfo.getUserId();
        userFavoriteService.removeFavorite(userId, articleId);
        return Result.success();
    }

    // 查询当前用户是否已收藏该文章，用于详情页星标回显
    @GetMapping("/check/{articleId}")
    public Result<Boolean> checkFavorite(@NotBlank(message = "文章ID不能为空") @PathVariable String articleId) {
        Long userId = GetUserInfo.getUserId();
        return Result.success(userFavoriteService.isFavorited(userId, articleId));
    }

    // 我的收藏分页列表
    @GetMapping("/page")
    public Result<Page<FavoriteArticleResponseDTO>> getMyFavorites(@Valid FavoriteQueryDTO queryDTO) {
        Long userId = GetUserInfo.getUserId();
        return Result.success(userFavoriteService.getMyFavorites(userId, queryDTO));
    }
}
