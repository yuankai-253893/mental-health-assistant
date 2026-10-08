package com.yuankai.aispringboot.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuankai.aispringboot.DTO.command.ArticleCreateDTO;
import com.yuankai.aispringboot.DTO.command.ArticleStatusUpdateDTO;
import com.yuankai.aispringboot.DTO.command.ArticleUpdateDTO;
import com.yuankai.aispringboot.DTO.query.ArticleListQueryDTO;
import com.yuankai.aispringboot.DTO.query.ArticlePageQueryDTO;
import com.yuankai.aispringboot.DTO.response.ArticleResponseDTO;
import com.yuankai.aispringboot.DTO.response.ArticleSimpleResponseDTO;
import com.yuankai.aispringboot.DTO.response.CategoryResponseDTO;
import com.yuankai.aispringboot.consts.RedisKeyConsts;
import com.yuankai.aispringboot.entity.KnowledgeArticle;
import com.yuankai.aispringboot.enumclass.UserType;
import com.yuankai.aispringboot.entity.KnowledgeCategory;
import com.yuankai.aispringboot.exception.BusinessException;
import com.yuankai.aispringboot.mapper.KnowledgeCategoryMapper;
import com.yuankai.aispringboot.mapper.KnowledgeArticleMapper;
import com.yuankai.aispringboot.service.convert.KnowledgeCategoryConvert;
import com.yuankai.aispringboot.util.RedisCounterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class KnowledgeCategoryService {
    // 分类树缓存：静态数据读多写少，全量缓存命中率最高
    private static final long CATEGORY_TREE_TTL_HOURS = 1;                      // 缓存有效期 1 小时

    // 文章阅读量：Redis 存增量（INCR 原子自增），MySQL 存基线值，定时任务把增量刷回库

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCounterUtil redisCounterUtil;

    // 注入 Spring Boot 自动配置的 ObjectMapper（Jackson 3，已注册 JavaTimeModule，支持 LocalDateTime 序列化）
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeCategoryMapper knowledgeCategoryMapper;

    @Autowired
    private KnowledgeArticleMapper knowledgeArticleMapper;

    @Autowired
    private KnowledgeCategoryConvert knowledgeCategoryConvert;

    @Autowired
    private SysFileInfoService sysFileInfoService;

    public List<CategoryResponseDTO> getCategoryTree() {
        // 1. 缓存优先：命中直接返回，避免每次全表查询（Cache Aside 读路径）
        try {
            String cached = redisTemplate.opsForValue().get(RedisKeyConsts.CATEGORY_TREE_KEY);
            if (StrUtil.isNotBlank(cached)) {
                log.info("分类树缓存命中");
                return objectMapper.readValue(cached, new TypeReference<List<CategoryResponseDTO>>() {});
            }
        } catch (Exception e) {
            // 缓存反序列化失败不阻塞主流程，回退查库
            log.warn("分类树缓存读取失败，回退数据库查询", e);
        }

        // 2. 缓存未命中 → 查库并挂树
        List<CategoryResponseDTO> tree = buildCategoryTreeFromDb();

        // 3. 回填缓存（TTL 兜底）
        try {
            redisTemplate.opsForValue().set(RedisKeyConsts.CATEGORY_TREE_KEY, objectMapper.writeValueAsString(tree),
                    CATEGORY_TREE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("分类树缓存写入失败", e);
        }
        return tree;
    }

    // 从数据库构建分类树
    private List<CategoryResponseDTO> buildCategoryTreeFromDb() {
        // 查询所有启用状态(status = 1)的分类，并按 sort_order 升序排列
        LambdaQueryWrapper<KnowledgeCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeCategory::getStatus, 1)
                .orderByAsc(KnowledgeCategory::getSortOrder);
        List<KnowledgeCategory> categories = knowledgeCategoryMapper.selectList(queryWrapper);

        // 把所有分类转成 DTO，并放进 Map（key=分类id），方便后面按 id 找父分类
        Map<Long, CategoryResponseDTO> dtoMap = new HashMap<>();
        for (KnowledgeCategory category : categories) {
            CategoryResponseDTO dto = knowledgeCategoryConvert.knowledgeCategoryToDTO(category);
            dtoMap.put(dto.getId(), dto);
        }

        // 遍历所有 DTO，挂到父分类的 children 下；parent_id=0 的作为顶级分类
        List<CategoryResponseDTO> tree = new ArrayList<>();
        for (CategoryResponseDTO dto : dtoMap.values()) {
            // 找到父分类
            CategoryResponseDTO parent = dtoMap.get(dto.getParentId());
            if (parent == null) {
                // 父分类不存在（parentId=0 或父分类已被禁用）→ 作为顶级分类
                tree.add(dto);
            } else {
                // 有父分类 → 挂到父分类的 children 下
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(dto);
            }
        }

        return tree;
    }

    // 说明：分类树只有 Cache Aside 的「读路径」，没有「写路径」（主动删缓存）。
    // 因为分类表目前只暴露了查询接口（GET /knowledge/category/tree），
    // 增删改由数据库初始化脚本维护，不存在「改了分类要立刻让缓存失效」的场景，
    // 靠 CATEGORY_TREE_TTL_HOURS 的 TTL 兜底已足够。
    // redisTemplate.delete(RedisKeyConsts.CATEGORY_TREE_KEY);

    // 管理员端分页查询文章
    public Page<ArticleSimpleResponseDTO> getArticleByPage(ArticleListQueryDTO queryDTO) {
        // 构建分页对象
        Page<KnowledgeArticle> page = new Page<>(queryDTO.getCurrentPage(), queryDTO.getSize());
        // 构建查询条件：根据分类ID，文章标题查询文章
        LambdaQueryWrapper<KnowledgeArticle> queryWrapper = new LambdaQueryWrapper<>();
        // 条件式查询：参数有值才拼接条件，避免 category_id = null / title LIKE null 查不到数据
        queryWrapper.eq(queryDTO.getCategoryId() != null, KnowledgeArticle::getCategoryId, queryDTO.getCategoryId())
                .like(StrUtil.isNotBlank(queryDTO.getTitle()), KnowledgeArticle::getTitle, queryDTO.getTitle());

        // 如果文章状态存在，则还要使用文章状态查询
        if (queryDTO.getStatus() != null) {
            queryWrapper.eq(KnowledgeArticle::getStatus, queryDTO.getStatus());
        }

        // 如果作者名字存在，则还要使用作者名字查询
        if (StrUtil.isNotBlank(queryDTO.getAuthorName())) {
            // 通过user表将string类型的作者名字转换为long类型的作者id
            Long authorId = knowledgeCategoryConvert.getUserIdByName(queryDTO.getAuthorName());
            // 作者名不存在时不追加条件（而不是拼 author_id = null）
            queryWrapper.eq(authorId != null, KnowledgeArticle::getAuthorId, authorId);
        }

        // 按发布时间倒序排列（新发布的文章在前）
        queryWrapper.orderByDesc(KnowledgeArticle::getPublishAt);
        Page<KnowledgeArticle> articlePage = knowledgeArticleMapper.selectPage(page, queryWrapper);

        // 合成阅读量：DB 基线 + Redis 增量（增量未刷库时列表展示也准确）
        articlePage.getRecords().forEach(a -> a.setReadCount(getDisplayReadCount(a)));
        Page<ArticleSimpleResponseDTO> responsePage = new Page<>(articlePage.getCurrent(), articlePage.getSize(), articlePage.getTotal());
        responsePage.setRecords(articlePage.getRecords().stream().map(knowledgeCategoryConvert::convertToSimpleResponseDTO).toList());
        return responsePage;
    }

    // 用户端分页查询文章
    public Page<ArticleSimpleResponseDTO> getArticleByPage(ArticlePageQueryDTO queryDTO) {
        Page<KnowledgeArticle> page = new Page<>(queryDTO.getCurrentPage(), queryDTO.getSize());
        LambdaQueryWrapper<KnowledgeArticle> queryWrapper = new LambdaQueryWrapper<>();

        // 只查询已发布文章（status=1）
        queryWrapper.eq(KnowledgeArticle::getStatus, 1);
        // 分类筛选：传了 categoryId 才拼接条件，null 表示「全部」
        queryWrapper.eq(queryDTO.getCategoryId() != null, KnowledgeArticle::getCategoryId, queryDTO.getCategoryId());

        // 按 sortField / sortDirection 动态排序（sortDirection 默认 desc，sortField 默认 readCount）
        // 阅读量排序基于数据库基线值，Redis 增量未刷库时排序略有滞后，属最终一致可接受
        // @Pattern 允许 null 通过，这里兜底默认值，避免 switch 收到 null 抛 NPE
        String sortField = StrUtil.blankToDefault(queryDTO.getSortField(), "readCount");
        boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortDirection());
        switch (sortField) {
            case "publishAt" -> queryWrapper.orderBy(true, isAsc, KnowledgeArticle::getPublishAt);
            case "createdAt" -> queryWrapper.orderBy(true, isAsc, KnowledgeArticle::getCreatedAt);
            default -> queryWrapper.orderBy(true, isAsc, KnowledgeArticle::getReadCount);
        }
        Page<KnowledgeArticle> articlePage = knowledgeArticleMapper.selectPage(page, queryWrapper);

        // 合成阅读量：DB 基线 + Redis 增量
        articlePage.getRecords().forEach(a -> a.setReadCount(getDisplayReadCount(a)));
        Page<ArticleSimpleResponseDTO> responsePage = new Page<>(articlePage.getCurrent(), articlePage.getSize(), articlePage.getTotal());
        responsePage.setRecords(articlePage.getRecords().stream().map(knowledgeCategoryConvert::convertToSimpleResponseDTO).toList());
        return responsePage;
    }

    // 管理员端创建文章
    public ArticleResponseDTO createArticle(ArticleCreateDTO articleDTO, Long userId) {
        log.info("用户{}创建文章", userId);
        // 校验文章ID不重复
        if (articleDTO.getId() != null && knowledgeArticleMapper.selectById(articleDTO.getId()) != null) {
            throw new BusinessException("该文章ID已存在");
        }
        // 校验文章分类存在
        if (knowledgeCategoryMapper.selectById(articleDTO.getCategoryId()) == null) {
            throw new BusinessException("文章分类不存在");
        }
        // 校验文章存在
        LambdaQueryWrapper<KnowledgeArticle> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeArticle::getTitle, articleDTO.getTitle())
                .eq(KnowledgeArticle::getCategoryId, articleDTO.getCategoryId());
        if (knowledgeArticleMapper.exists(queryWrapper))
            throw new BusinessException("该文章已存在");

        // 创建文章
        KnowledgeArticle knowledgeArticle = knowledgeCategoryConvert.convertToEntity(articleDTO, userId);

        // 输入数据库
        knowledgeArticleMapper.insert(knowledgeArticle);

        return knowledgeCategoryConvert.convertToResponseDTO(knowledgeArticle);
    }

    // 根据ID获取文章（roleType：当前用户角色，用于未发布文章的权限控制）
    public ArticleResponseDTO getArticleById(String id, Integer roleType) {
        // 判断文章是否存在
        KnowledgeArticle knowledgeArticle = knowledgeArticleMapper.selectById(id);
        if (knowledgeArticle == null) {
            throw new BusinessException("该文章不存在");
        }

        // 非已发布(草稿status=0、已下线status=2)的文章仅管理员可查看；普通用户即使知道id也不能看，防止越权
        if (!Integer.valueOf(1).equals(knowledgeArticle.getStatus()) && !UserType.ADMIN.getCode().equals(roleType)) {
            throw new BusinessException("该文章不存在或未发布");
        }

        // 阅读量+1：优先 Redis INCR 原子自增（内存操作，扛高并发）；Redis 不可用时降级为 SQL 原子自增
        try {
            redisCounterUtil.increment(RedisKeyConsts.ARTICLE_READ_KEY_PREFIX + id);
        } catch (Exception e) {
            log.warn("Redis 阅读量自增失败，降级为 SQL 原子自增", e);
            LambdaUpdateWrapper<KnowledgeArticle> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(KnowledgeArticle::getId, id)
                    .setSql("read_count = COALESCE(read_count, 0) + 1");
            knowledgeArticleMapper.update(null, updateWrapper);
        }

        // 展示阅读量 = 数据库基线值 + Redis 增量
        knowledgeArticle.setReadCount(getDisplayReadCount(knowledgeArticle));
        return knowledgeCategoryConvert.convertToResponseDTO(knowledgeArticle);
    }

    // 合成阅读量：数据库基线值 + Redis 中未刷库的增量（Redis 异常时回退基线值）
    private Integer getDisplayReadCount(KnowledgeArticle article) {
        int base = article.getReadCount() == null ? 0 : article.getReadCount();
        try {
            String incr = redisCounterUtil.get(RedisKeyConsts.ARTICLE_READ_KEY_PREFIX + article.getId());
            if (StrUtil.isNotBlank(incr)) {
                return base + Integer.parseInt(incr);
            }
        } catch (Exception e) {
            log.warn("读取阅读量增量失败，使用数据库基线值", e);
        }
        return base;
    }

    // 定时任务调用：把 Redis 中的阅读量增量刷回 MySQL（GETDEL 原子取增量并清零，刷完即删）
    public void syncArticleReadCounts() {
        Set<String> keys = redisCounterUtil.keys(RedisKeyConsts.ARTICLE_READ_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            try {
                // GETDEL 原子取出增量并删除：刷回期间新产生的点击留在新 key 里，不会被误删
                String val = redisCounterUtil.getAndDelete(key);
                if (StrUtil.isBlank(val)) {
                    continue;
                }
                int incr = Integer.parseInt(val);
                if (incr <= 0) {
                    continue;
                }
                String articleId = key.substring(RedisKeyConsts.ARTICLE_READ_KEY_PREFIX.length());
                LambdaUpdateWrapper<KnowledgeArticle> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(KnowledgeArticle::getId, articleId)
                        .setSql("read_count = COALESCE(read_count, 0) + " + incr);
                knowledgeArticleMapper.update(null, updateWrapper);
                log.info("阅读量刷库完成：文章 {} 增量 {}", articleId, incr);
            } catch (Exception e) {
                log.warn("阅读量刷库失败，key={}", key, e);
            }
        }
    }

    // 更新知识文章
    public ArticleResponseDTO updateArticle(String id,ArticleUpdateDTO articleDTO) {
        if (!id.equals(articleDTO.getId()))
            throw new BusinessException("传入文章ID不匹配");

        // 校验文章分类存在
        if (knowledgeCategoryMapper.selectById(articleDTO.getCategoryId()) == null) {
            throw new BusinessException("文章分类不存在");
        }

        KnowledgeArticle knowledgeArticle = knowledgeArticleMapper.selectById(id);
        if (knowledgeArticle == null) {
            throw new BusinessException("该文章不存在");
        }

        // 检查该分类id是否存在于知识文章分类表
        if (knowledgeCategoryMapper.selectById(articleDTO.getCategoryId()) == null) {
            throw new BusinessException("文章分类不存在");
        }

        // 更新文章信息
        // 先记下旧封面：更新成功后要把它对应的物理文件清理掉，否则磁盘只增不减
        String oldCover = knowledgeArticle.getCover();
        String newCover = articleDTO.getCoverImage();

        knowledgeArticle.setTitle(articleDTO.getTitle());
        knowledgeArticle.setContent(articleDTO.getContent());
        knowledgeArticle.setCover(newCover);
        knowledgeArticle.setCategoryId(articleDTO.getCategoryId());
        knowledgeArticle.setSummary(articleDTO.getSummary());
        knowledgeArticle.setTags(articleDTO.getTags());
        knowledgeArticle.setUpdatedAt(LocalDateTime.now());

        // 将新的文章信息更新到数据库
        knowledgeArticleMapper.updateById(knowledgeArticle);

        // 封面被替换或清空后清理旧文件。放在写库之后：此时旧 URL 已确定不再被引用，
        // 即使删文件失败也只留下一个孤儿文件，不会破坏文章数据。
        if (StrUtil.isNotBlank(oldCover) && !oldCover.equals(newCover)) {
            sysFileInfoService.deleteByUrlQuietly(oldCover);
        }

        ArticleResponseDTO result = knowledgeCategoryConvert.convertToResponseDTO(knowledgeArticle);

        return result;
    }

    // 更新文章状态
    public void updateArticleStatus(String id, ArticleStatusUpdateDTO updateDTO) {
        KnowledgeArticle knowledgeArticle = knowledgeArticleMapper.selectById(id);
        if (knowledgeArticle == null) {
            throw new BusinessException("该文章不存在");
        }

        // 状态取值：0-草稿，1-已发布，2-已下线
        Integer status = updateDTO.getStatus();
        if (status == null
                || (!Integer.valueOf(0).equals(status)
                && !Integer.valueOf(1).equals(status)
                && !Integer.valueOf(2).equals(status))) {
            throw new BusinessException("输入状态值错误");
        }

        knowledgeArticle.setStatus(status);
        // 发布时写入发布时间
        if (Integer.valueOf(1).equals(status)) {
            knowledgeArticle.setPublishAt(LocalDateTime.now());
        }
        knowledgeArticleMapper.updateById(knowledgeArticle);
    }

    // 删除文章
    public void deleteArticle(String id) {
        KnowledgeArticle knowledgeArticle = knowledgeArticleMapper.selectById(id);
        if (knowledgeArticle == null) {
            throw new BusinessException("该文章不存在");
        }
        knowledgeArticleMapper.deleteById(id);

        // 文章没了，封面文件也一并清理，否则会永远留在 upload 目录里
        sysFileInfoService.deleteByUrlQuietly(knowledgeArticle.getCover());
    }

}
