package com.yuankai.aispringboot.consts;

/**
 * Redis Key 常量集中管理。
 *
 * 注意：key 的"值"一经上线就不要改，否则旧缓存数据将无法命中（本项目数据均为短时效，影响可控）。
 */
public final class RedisKeyConsts {

    private RedisKeyConsts() {
    }

    /** JWT 登出黑名单前缀，完整 key = token:blacklist:{token} */
    public static final String BLACKLIST_PREFIX = "token:blacklist:";

    /** 登录失败次数计数前缀，完整 key = login:fail:{username}（防暴力破解） */
    public static final String LOGIN_FAIL_PREFIX = "login:fail:";

    /** 今日活跃用户（HyperLogLog），完整 key = active:user:{yyyy-MM-dd}，按天一个、TTL 3 天 */
    public static final String ACTIVE_KEY_PREFIX = "active:user:";

    /** 知识库分类树缓存键（全量缓存，TTL 1 小时） */
    public static final String CATEGORY_TREE_KEY = "knowledge:category:tree";

    /** 数据分析仪表盘聚合缓存键（整块响应缓存，TTL 60 秒） */
    public static final String DATA_ANALYTICS_OVERVIEW_KEY = "data:analytics:overview";

    /** 文章阅读量增量缓存前缀，完整 key = article:read:{articleId}（INCR 原子自增，定时刷库） */
    public static final String ARTICLE_READ_KEY_PREFIX = "article:read:";

    /** 注册 IP 限流前缀，完整 key = register:ip:{ip}（同 IP 一天最多注册 3 个账号，TTL 1 天） */
    public static final String REGISTER_LIMIT_PREFIX = "register:ip:";

    /**
     * 密码变更时间水位线，完整 key = user:pwd:changed:{userId}，值为秒级 epoch。
     * 签发时间早于该水位的 token 判定失效（"改密即踢下线"），
     * TTL 与 token 最长有效期一致，用户重新登录拿到新 token 后该 key 自然过期。
     */
    public static final String PASSWORD_CHANGED_PREFIX = "user:pwd:changed:";
}
