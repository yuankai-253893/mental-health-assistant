<template>
    <div class="articleDetail-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width: 60px;height: 60px"></el-image>
                <h1>知识文章详情</h1>
            </div>
        </div>
        <!-- 返回入口常驻：文章加载失败时也能回到列表 -->
        <div class="back-bar">
            <el-button link class="back-btn" @click="goBackToList">
                <el-icon><ArrowLeft /></el-icon>
                <span>返回知识库</span>
            </el-button>
        </div>
        <div class="content" v-if="articleDetail.id">
            <div class="diary-card">
                <p class="title">文章信息</p>
                <div class="sub-title">
                    <el-tag size="large" class="category-tag" v-if="categoryName">{{ categoryName }}</el-tag>
                    <div class="flex-box">
                        <el-icon><List /></el-icon>
                        <span>{{ dayjs(articleDetail.updatedAt).format('YYYY-MM-DD') }}</span>
                    </div>
                </div>
                <h1 class="article-title">{{ articleDetail.title }}</h1>
                <div class="summary-content" v-if="articleDetail.summary ">
                    <p>{{ articleDetail.summary }}</p>
                </div>
                <div :style="{marginTop: '20px'}" class="flex-box">
                   <div class="item flex-box">
                        <el-icon><Avatar /></el-icon>
                        <span>{{ articleDetail.authorName }}</span>
                    </div>
                    <div class="item flex-box">
                        <el-icon><Platform /></el-icon>
                        <span>{{ articleDetail.readCount }} 次阅读</span>
                    </div>
                </div>
            </div>
            <div class="diary-card">
                <div class="title">正文内容</div>
                <div class="content-wrapper" v-html="formatContent(articleDetail.content)"></div>
                <div class="tags-content" v-if="tagList.length">
                    <h4 class="tags-title"> 相关标签 </h4>
                    <div class="tags-list">
                        <el-tag v-for="tag in tagList" :key="tag" type="info" effect="light" class="tag-item">{{ tag }}</el-tag>
                    </div>
                </div>
            </div>
        </div>
        <!-- 文章不存在、已下线，或加载失败时给出明确提示，避免整页空白 -->
        <el-empty v-else-if="loadFailed" description="文章不存在或已下线" class="empty-tip" />
    </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getKnowledgeDetail, getKnowledgeCategoryTree } from '@/api/user'
import { dayjs } from 'element-plus'
import { Avatar, ArrowLeft } from '@element-plus/icons-vue'
import iconUrl from '@/assets/images/book.png'

const props = defineProps({
    id: String
})

const router = useRouter()

// 返回知识库列表
const goBackToList = () => {
    router.push('/knowledge')
}

const articleDetail = ref({})
// 加载失败（文章不存在、已下线、网络异常）时置为 true，用于展示空状态
const loadFailed = ref(false)

// 分类 id -> 名称：详情接口只返回 categoryId，没有 categoryName
const categoryNameMap = ref({})
const categoryName = computed(() => categoryNameMap.value[articleDetail.value.categoryId] || '')

// 详情接口的 tags 是逗号分隔的字符串，需要拆成数组渲染
const tagList = computed(() => {
    const tags = articleDetail.value.tags
    if (!tags) return []
    return tags.split(',').map(tag => tag.trim()).filter(Boolean)
})

const loadCategoryMap = () => {
    getKnowledgeCategoryTree().then(res => {
        const map = {}
        const walk = (list) => {
            (list || []).forEach(item => {
                map[item.id] = item.categoryName
                if (item.children && item.children.length) {
                    walk(item.children)
                }
            })
        }
        walk(res)
        categoryNameMap.value = map
    }).catch(() => {
        // 分类名加载失败只是不显示分类标签
    })
}

const formatContent = (content) => {
  if (!content) return ''
  
  // 基本的HTML清理和格式化
  let formatted = content
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
  
  return formatted
}

onMounted(() => {
    loadCategoryMap()
    getKnowledgeDetail(props.id).then(res => {
        articleDetail.value = res
    }).catch(() => {
        // 失败提示已由 request.js 拦截器统一弹出，这里只需要展示空状态
        loadFailed.value = true
    })
})
</script>

<style lang="scss" scoped>
.articleDetail-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    .flex-box {
        display: flex;
        align-items: center;
        .item {
            margin-right: 20px;
            span {
                margin-left: 5px;
            }
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
    .back-bar {
        max-width: 980px;
        margin: 0 auto;
        box-sizing: border-box;
        padding: 16px 20px 0;
        .back-btn {
            font-size: 14px;
            color: #6b7280;
            span {
                margin-left: 4px;
            }
            &:hover {
                color: #f59e0b;
            }
        }
    }
    .content {
        margin: 0 auto;
        width: 100%;
        max-width: 980px;
        box-sizing: border-box;
        padding: 20px;
        .diary-card {
            margin-bottom: 20px;
            background: white;
            border-radius: 10px;
            padding: 20px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
            .title {
                margin-bottom: 15px;
                font-size: 20px;
                font-weight: 600;
                color: #374151;
            }
            .sub-title {
                margin-top: 20px;
                display: flex;
                align-items: center;
                .category-tag {
                    margin-right: 20px;
                }
            }
            .article-title {
                font-size: 28px;
                font-weight: bold;
                color: #111827;
                margin-top: 30px;
                margin-bottom: 10px;
            }
            .summary-content {
                background: rgba(126, 211, 33, 0.1);
                border-left: 4px solid #7ED321;
                padding: 10px 15px;
                border-radius: 0 8px 8px 0;
                position: relative;
            }
            .content-wrapper {
                font-size: 15px;
                color: #374151;
                :deep(p) {
                    margin-bottom: 10px;
                }
                :deep(h1),
                :deep(h2),
                :deep(h3),
                :deep(h4),
                :deep(h5),
                :deep(h6) {
                    margin: 15px 0 10px;
                    color: #111827;
                    font-weight: 600;
                }
                :deep(h2) {
                    font-size: 15px;
                    border-bottom: 2px solid #e5e7eb;
                    padding-bottom: 5px;
                }
                :deep(h3) {
                    font-size: 13px;
                }
                :deep(ul),
                :deep(ol) {
                    padding-left: 15px;
                    margin-bottom: 10px;
                }
                :deep(li) {
                    margin-bottom: 5px;
                }
            }
            .tags-content {
                margin-top: 20px;
                padding-top: 15px;
                border-top: 1px solid #e5e7eb;
                .tags-title {
                    margin-bottom: 10px;
                    font-size: 14px;
                    font-weight: 600;
                    color: #374151;
                }
                .tags-list {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 10px;
                }
            }
        }
    }

    .empty-tip {
        padding: 80px 0;
    }

    /* 移动端：收紧内边距并缩小标题 */
    @media (max-width: 768px) {
        .header-section {
            padding: 24px 16px;
            .header-content h1 {
                font-size: 22px;
            }
        }
        .back-bar {
            padding: 12px 12px 0;
        }
        .content {
            padding: 12px;
            .diary-card {
                padding: 15px;
                .article-title {
                    font-size: 22px;
                }
            }
        }
    }
}
</style>