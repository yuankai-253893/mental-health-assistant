<template>
    <div class="favorites-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width: 60px;height: 60px"></el-image>
                <h1>我的收藏</h1>
            </div>
        </div>
        <div class="content">
            <!-- 收藏列表 -->
            <div v-if="favoriteList.length" class="favorite-list">
                <div
                    v-for="item in favoriteList"
                    :key="item.favoriteId"
                    class="favorite-item"
                    @click="goToArticle(item.articleId)">
                    <el-image class="cover" style="width: 240px; height: 150px" :src="getCover(item)"></el-image>
                    <div class="info">
                        <h3 class="item-title">{{ item.title }}</h3>
                        <p class="summary" v-if="item.summary">{{ item.summary }}</p>
                        <div class="meta">
                            <span class="meta-item">
                                <el-icon><Avatar /></el-icon>
                                <span>{{ item.authorName || '佚名' }}</span>
                            </span>
                            <span class="meta-item">
                                <el-icon><Platform /></el-icon>
                                <span>阅读 {{ item.readCount }}</span>
                            </span>
                            <span class="meta-item">
                                <el-icon><StarFilled /></el-icon>
                                <span>{{ dayjs(item.favoriteAt).format('YYYY-MM-DD HH:mm') }} 收藏</span>
                            </span>
                        </div>
                    </div>
                    <div class="item-actions">
                        <el-button text type="danger" @click.stop="handleRemove(item)">
                            <el-icon><DeleteFilled /></el-icon>
                            <span>取消收藏</span>
                        </el-button>
                    </div>
                </div>
            </div>
            <!-- 空状态 -->
            <el-empty v-else-if="!loading" description="还没有收藏任何文章">
                <el-button type="primary" @click="goToKnowledge">去知识库看看</el-button>
            </el-empty>
        </div>
        <!-- 分页 -->
        <div class="pagination-wrapper" v-if="pagination.total > pagination.size">
            <el-pagination
                v-model:current-page="pagination.currentPage"
                v-model:page-size="pagination.size"
                layout="prev, pager, next"
                :total="pagination.total"
                @change="loadFavorites" />
        </div>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
import { Avatar, Platform, StarFilled, DeleteFilled } from '@element-plus/icons-vue'
import { getFavoritePage, removeFavorite } from '@/api/user'
import { fileBaseUrl } from '@/config/index.js'
import iconUrl from '@/assets/images/like.png'
import coverPlaceholder from '@/assets/images/hero.png'

const router = useRouter()

const favoriteList = ref([])
const loading = ref(false)

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const loadFavorites = () => {
    loading.value = true
    getFavoritePage({
        currentPage: pagination.currentPage,
        size: pagination.size
    }).then(res => {
        favoriteList.value = res.records || []
        pagination.total = res.total || 0
    }).catch(() => {
        // 失败提示已由 request.js 拦截器统一弹出
    }).finally(() => {
        loading.value = false
    })
}

// 列表接口返回相对路径，需要拼上文件服务地址
const getCover = (item) => {
    return item.cover ? fileBaseUrl + item.cover : coverPlaceholder
}

const goToArticle = (articleId) => {
    router.push(`/knowledge/article/${articleId}`)
}

const goToKnowledge = () => {
    router.push('/knowledge')
}

// 取消收藏：先确认再执行，执行后若当前页空了则回退一页
const handleRemove = (item) => {
    ElMessageBox.confirm(`确定要取消收藏《${item.title}》吗？`, '取消收藏', {
        confirmButtonText: '确定',
        cancelButtonText: '再想想',
        type: 'warning'
    }).then(async () => {
        await removeFavorite(item.articleId)
        ElMessage.success('已取消收藏')
        // 当前页只剩这一条时回退一页，避免停留在空页
        if (favoriteList.value.length === 1 && pagination.currentPage > 1) {
            pagination.currentPage -= 1
        }
        loadFavorites()
    }).catch(() => {
        // 用户取消操作 或 接口失败（提示已由拦截器弹出），无需额外处理
    })
}

onMounted(loadFavorites)
</script>

<style lang="scss" scoped>
.favorites-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    min-height: 60vh;

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
        margin: 0 auto;
        width: 100%;
        max-width: 980px;
        box-sizing: border-box;
        padding: 20px;
        min-height: 320px;

        .favorite-item {
            background: white;
            border-radius: 12px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
            padding: 15px;
            margin-bottom: 20px;
            display: flex;
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s;

            &:hover {
                transform: translateY(-2px);
                box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
            }

            /* 封面固定宽度，不允许被 flex 压缩 */
            :deep(.el-image) {
                flex-shrink: 0;
            }

            .info {
                flex: 1;
                /* flex item 默认 min-width: auto，不设 0 会被固定宽封面撑破容器 */
                min-width: 0;
                margin-left: 20px;
                display: flex;
                flex-direction: column;

                .item-title {
                    font-size: 18px;
                    color: #111827;
                    margin-bottom: 10px;
                }

                .summary {
                    font-size: 14px;
                    color: #6b7280;
                    line-height: 1.6;
                    /* 摘要最多两行 */
                    display: -webkit-box;
                    -webkit-line-clamp: 2;
                    -webkit-box-orient: vertical;
                    overflow: hidden;
                    margin-bottom: 12px;
                }

                .meta {
                    margin-top: auto;
                    display: flex;
                    flex-wrap: wrap;
                    gap: 20px;
                    font-size: 13px;
                    color: #9ca3af;

                    .meta-item {
                        display: flex;
                        align-items: center;
                        gap: 5px;
                    }
                }
            }

            .item-actions {
                display: flex;
                align-items: flex-start;
            }
        }
    }

    .pagination-wrapper {
        display: flex;
        justify-content: center;
        padding-bottom: 30px;
    }

    /* 移动端：封面与文字改为上下排列 */
    @media (max-width: 768px) {
        .header-section {
            padding: 24px 16px;

            .header-content h1 {
                font-size: 22px;
            }
        }

        .content {
            padding: 12px;

            .favorite-item {
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
