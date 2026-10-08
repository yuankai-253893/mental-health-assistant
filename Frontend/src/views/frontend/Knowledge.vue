<template>
  <div class="knowledge-container">
    <div class="header-section">
        <div class="header-content">
            <el-image :src="iconUrl" style="width: 60px;height: 60px"></el-image>
            <h1>知识库</h1>
        </div>
    </div>
    <div class="content">
        <!-- 左侧菜单 -->
         <div class="recommend-section">
            <div class="section-title">推荐阅读</div>
            <div class="recommend-list">
                <div v-for="item in recommendList" :key="item.id" class="recommend-item" @click="goToArticle(item.id)">
                    <h4>{{item.title}}</h4>
                    <p class="read-count">
                        <el-icon><Histogram /></el-icon>
                            阅读量 {{ item.readCount }}
                    </p>
                </div>
            </div>
         </div>
         <!-- 右侧内容 -->
         <div class="article-list">
            <!-- 分类导航：「全部」+ 分类树里的所有分类，点击即筛选 -->
            <div class="category-nav">
                <span
                    class="category-item"
                    :class="{ active: activeCategoryId === null }"
                    @click="selectCategory(null)">全部</span>
                <span
                    v-for="cat in categoryList"
                    :key="cat.id"
                    class="category-item"
                    :class="{ active: activeCategoryId === cat.id }"
                    @click="selectCategory(cat.id)">{{ cat.categoryName }}</span>
            </div>
            <div v-for="item in articleList" :key="item.id" class="article-item" @click="goToArticle(item.id)">
                <el-image style="width: 240px; height: 150px" :src="getCover(item)"></el-image>
                <div class="info">
                    <div class="title">
                        <h3>{{ item.title }}</h3>
                        <!-- 列表接口只返回 categoryId，分类名由分类树映射得到 -->
                        <el-tag v-if="categoryNameMap[item.categoryId]" effect="plain" type="primary">
                            {{ categoryNameMap[item.categoryId] }}
                        </el-tag>
                    </div>
                    <div :style="{marginTop: '10px'}">
                        <div class="flex-box">
                            <el-icon><Avatar /></el-icon>
                            <span>{{ item.authorName }}</span>
                        </div>
                        <div class="flex-box">
                            <el-icon><List /></el-icon>
                            <span>{{ dayjs(item.updatedAt).format('YYYY-MM-DD') }}</span>
                        </div>
                    </div>
                    <div :style="{marginTop: '10px'}">
                        <div class="flex-box">
                            <el-icon><Platform /></el-icon>
                            <span>阅读次数 {{ item.readCount }}</span>
                        </div>
                    </div>
                </div>
            </div>
         </div>
    </div>
    <!-- 分页 -->
    <div class="pagination-wrapper">
    <el-pagination 
        style="margin-top: 25px"
        v-model:current-page="pagination.currentPage"
        v-model:page-size="pagination.size"
        layout="prev, pager, next"
        :total="pagination.total"
        @change="handleChange" />
    </div>
  </div>
</template>
<script setup>
    import { dayjs } from 'element-plus'
    import { ref, reactive, onMounted } from 'vue'
    import { getArticlePage, getCategoryTree } from '@/api/knowledge'
    import { useRouter } from 'vue-router'
    import iconUrl from '@/assets/images/book.png'
    import coverPlaceholder from '@/assets/images/hero.png'
    import { Platform } from '@element-plus/icons-vue'
    import { fileBaseUrl } from '@/config/index.js'

    const router = useRouter()

    // 推荐阅读列表
    const recommendList = ref([])

    // 分类 id -> 分类名称。列表接口只返回 categoryId，没有 categoryName，用分类树补上
    const categoryNameMap = ref({})

    // 分类导航数据源（把分类树拍平成一行可点击的筛选项）
    const categoryList = ref([])
    // 当前选中的分类，null 表示「全部」
    const activeCategoryId = ref(null)

    // 右侧列表数据
    const pagination = reactive({
        currentPage: 1,
        size: 10,
        total: 0
    })

    const articleList = ref([])
    // 获取列表数据
    const getPageList = () => {
        // sortField 后端只接受 readCount / publishAt / createdAt，且不支持额外传 total
        const params = {
            currentPage: pagination.currentPage,
            size: pagination.size,
            sortField: 'publishAt',
            sortDirection: 'desc'
        }
        // 选了具体分类才传 categoryId，避免传空字符串导致后端 Long 绑定失败
        if (activeCategoryId.value !== null) {
            params.categoryId = activeCategoryId.value
        }
        getArticlePage(params).then(res => {
            articleList.value = res.records
            pagination.total = res.total
        }).catch(() => {
            // 失败提示已由 request.js 拦截器统一弹出
        })
    }
    // 获取封面图片：列表接口返回的是相对路径，需要拼上文件服务地址
    const getCover = (item) => {
        return item.cover ? fileBaseUrl + item.cover : coverPlaceholder
    }

    // 构建分类 id -> 名称映射（分类树既可能是扁平的，也可能是嵌套的，这里都兼容），
    // 同时拍平成分类导航的数据源
    const loadCategoryMap = () => {
        getCategoryTree().then(res => {
            const map = {}
            const flat = []
            const walk = (list) => {
                (list || []).forEach(item => {
                    map[item.id] = item.categoryName
                    flat.push({ id: item.id, categoryName: item.categoryName })
                    if (item.children && item.children.length) {
                        walk(item.children)
                    }
                })
            }
            walk(res)
            categoryNameMap.value = map
            categoryList.value = flat
        }).catch(() => {
            // 分类名加载失败时只是不显示分类标签，不影响文章列表
        })
    }

    // 切换分类：换分类后回到第一页，避免停留在越界页码看到空白
    const selectCategory = (id) => {
        if (activeCategoryId.value === id) return
        activeCategoryId.value = id
        pagination.currentPage = 1
        getPageList()
    }

    const handleChange = (page) => {
        pagination.currentPage = page
        getPageList()
    }

    // 跳转到详情
    const goToArticle = (id) => {
        router.push(`/knowledge/article/${id}`)
    }

    onMounted(() => {
        getPageList()
        loadCategoryMap()
        // 推荐阅读：按阅读量倒序取前 5 篇
        getArticlePage({
            sortField: 'readCount',
            sortDirection: 'desc',
            currentPage: 1,
            size: 5
        }).then(res => {
            recommendList.value = res.records
        }).catch(() => {
            // 同上，失败提示由 request.js 统一处理
        })
    })
</script>
<style lang="scss" scoped>
    .knowledge-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    .flex-box {
        display: flex;
        align-items: center;
        span {
            margin-left: 10px;
        }
    }
    .header-section {
        background: linear-gradient(135deg, #f59e0b 0%, #8b5cf6 100%);
        color: white;
        padding: 48px;
        .header-content {
            display: flex;
            align-items: center;
            gap: 12px;
        }
    }
    .content {
        display: flex;
        gap: 20px;
        margin: 0 auto;
        width: 100%;
        max-width: 1200px;
        box-sizing: border-box;
        padding: 20px;
        .recommend-section {
            width: 280px;
            flex-shrink: 0;
            /* 必须用 border-box：窄屏媒体查询里会改成 width: 100%，
               否则 content-box 下 15px 内边距会额外撑出 30px 造成页面横向溢出 */
            box-sizing: border-box;
            background: white;
            border-radius: 12px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
            padding: 15px;
            min-height: 400px;
            .section-title {
                font-size: 12px;
                font-weight: 600;
                color: #374151;
                margin-bottom: 10px;
                display: flex;
                align-items: center;
                gap: 5px;
            }
            .recommend-list {
                display: flex;
                flex-direction: column;
                gap: 1rem;
                .recommend-item {
                    border-left: 4px solid #f59e0b;
                    padding-left: 10px;
                    cursor: pointer;
                    .read-count {
                        margin-top: 15px;
                        font-size: 12px;
                        color: #6b7280;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                }
            }
        }
        .article-list {
            flex: 1;
            /* flex item 默认 min-width: auto，不设 0 会被内部固定宽度撑破容器导致横向溢出 */
            min-width: 0;
            .category-nav {
                display: flex;
                flex-wrap: wrap;
                gap: 10px;
                margin-bottom: 16px;
                .category-item {
                    padding: 4px 14px;
                    font-size: 13px;
                    color: #4b5563;
                    background: white;
                    border-radius: 16px;
                    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
                    cursor: pointer;
                    transition: all 0.2s;
                    &:hover {
                        color: #f59e0b;
                    }
                    &.active {
                        color: white;
                        background: linear-gradient(135deg, #f59e0b 0%, #8b5cf6 100%);
                    }
                }
            }
            .article-item {
                background: white;
                border-radius: 12px;
                box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
                padding: 15px;
                margin-bottom: 20px;
                display: flex;
                cursor: pointer;
                /* 封面图为固定 240px 宽，不允许被压缩 */
                :deep(.el-image) {
                    flex-shrink: 0;
                }
                .info {
                    margin-left: 20px;
                    /* flex item 默认 min-width: auto，会导致固定宽封面图把行撑宽 */
                    min-width: 0;
                    .title {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                }
            }
        }
    }
    .pagination-wrapper {
        display: flex;
        justify-content: center;
        padding-bottom: 30px;
    }

    /* 平板：推荐阅读挪到文章列表上方，避免侧栏把列表挤窄 */
    @media (max-width: 1024px) {
        .content {
            flex-direction: column;
            .recommend-section {
                width: 100%;
                min-height: auto;
            }
        }
    }

    /* 移动端：封面图与文字改为上下排列 */
    @media (max-width: 768px) {
        .header-section {
            padding: 24px 16px;
            .header-content h1 {
                font-size: 22px;
            }
        }
        .content {
            padding: 12px;
            .article-item {
                flex-direction: column;
                .info {
                    margin-left: 0;
                    margin-top: 12px;
                }
            }
        }
    }
}
</style>