<template>
    <div>
        <PageHead title="咨询记录" />
        <TableSearch :formItem="formItem" @search="handleSearch" />
        <el-table :data="tableData" v-loading="loading" style="width: 100%; margin-top: 25px" empty-text="暂无咨询记录">
            <el-table-column label="会话ID" width="90">
                <template #default="scope">
                    <el-tag size="small" type="info">#{{ scope.row.id }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="用户" width="160">
                <template #default="scope">
                    <div class="user-cell">
                        <span class="user-name">{{ scope.row.username || '-' }}</span>
                        <span class="user-id">ID：{{ scope.row.userId }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="会话内容" min-width="240">
                <template #default="scope">
                    <div class="session-title">{{ scope.row.sessionTitle }}</div>
                    <div class="session-preview">{{ scope.row.lastMessageContent || '暂无消息' }}</div>
                </template>
            </el-table-column>
            <el-table-column label="情绪" width="130">
                <template #default="scope">
                    <el-tag
                        v-if="emotionMap[scope.row.id]"
                        size="small"
                        :type="getEmotionTagType(emotionMap[scope.row.id].primaryEmotion)"
                    >
                        {{ emotionMap[scope.row.id].icon }} {{ emotionMap[scope.row.id].primaryEmotion || '未知' }}
                    </el-tag>
                    <span v-else class="text-muted">未分析</span>
                </template>
            </el-table-column>
            <el-table-column prop="messageCount" label="消息数" width="90" />
            <el-table-column label="最后消息时间" width="170">
                <template #default="scope">
                    <span>{{ formatDateTime(scope.row.lastMessageTime) }}</span>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
                <template #default="scope">
                    <el-button type="primary" text @click="viewSessionDetail(scope.row)">详情</el-button>
                    <el-button type="warning" text @click="handleRename(scope.row)">改名</el-button>
                    <el-button type="danger" text @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination
            style="margin-top: 25px"
            v-model:current-page="pagination.currentPage"
            :page-size="pagination.size"
            layout="prev, pager, next, total"
            :total="pagination.total"
            @current-change="handleChange"
        />
        <el-dialog
            v-model="showDetailDialog"
            title="咨询会话详情"
            width="70%"
            :close-on-click-modal="false"
            @closed="handleDetailClosed"
        >
            <div class="session-detail">
                <div class="detail-header">
                    <div class="detail-row">
                        <div class="detail-label">用户：</div>
                        <div class="detail-value">{{ sessionDetail.username || '-' }}（ID：{{ sessionDetail.userId }}）</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">开始时间：</div>
                        <div class="detail-value">{{ formatDateTime(sessionDetail.startedAt) }}</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">消息数：</div>
                        <div class="detail-value">{{ sessionDetail.messageCount ?? 0 }}</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">最后消息：</div>
                        <div class="detail-value">{{ formatDateTime(sessionDetail.lastMessageTime) }}</div>
                    </div>
                </div>

                <!-- 会话情绪分析：数据源是咨询会话表上的 last_emotion_analysis(JSON 字符串) -->
                <div class="emotion-block" v-if="detailEmotion">
                    <div class="block-header">
                        <h4>情绪分析</h4>
                        <span class="block-time">分析时间：{{ formatDateTime(sessionDetail.lastEmotionUpdatedAt) }}</span>
                    </div>
                    <el-descriptions :column="2" border size="small">
                        <el-descriptions-item label="主要情绪">
                            <el-tag :type="getEmotionTagType(detailEmotion.primaryEmotion)">
                                {{ detailEmotion.icon }} {{ detailEmotion.primaryEmotion || '未知' }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="情绪积极度">
                            <el-progress
                                :percentage="detailEmotion.emotionScore ?? 0"
                                :color="getEmotionScoreColor(detailEmotion.emotionScore)"
                                :stroke-width="8"
                            />
                        </el-descriptions-item>
                        <el-descriptions-item label="风险等级">
                            <el-tag :type="getRiskTagType(detailEmotion.riskLevel)">
                                {{ riskLevelText(detailEmotion.riskLevel) }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="关键词">
                            <template v-if="detailEmotion.keywords && detailEmotion.keywords.length">
                                <el-tag
                                    v-for="kw in detailEmotion.keywords"
                                    :key="kw"
                                    size="small"
                                    effect="plain"
                                    class="keyword-tag"
                                >{{ kw }}</el-tag>
                            </template>
                            <span v-else>-</span>
                        </el-descriptions-item>
                        <el-descriptions-item label="关怀建议" :span="2">
                            {{ detailEmotion.suggestion || '-' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="风险提示" :span="2">
                            {{ detailEmotion.riskDescription || '-' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="治愈小行动" :span="2">
                            <ul
                                v-if="detailEmotion.improvementSuggestions && detailEmotion.improvementSuggestions.length"
                                class="improvement-list"
                            >
                                <li v-for="(item, idx) in detailEmotion.improvementSuggestions" :key="idx">{{ item }}</li>
                            </ul>
                            <span v-else>-</span>
                        </el-descriptions-item>
                    </el-descriptions>
                </div>
                <el-alert
                    v-else
                    class="emotion-empty"
                    type="info"
                    :closable="false"
                    show-icon
                    title="该会话暂无情绪分析结果"
                    description="用户产生新对话后，系统会自动分析会话情绪并在此展示。"
                />

                <div class="messages-container">
                    <div class="messages-header">
                        <h4>对话记录</h4>
                    </div>
                    <div class="messages-list" v-loading="loadingMessages" ref="messagesListRef">
                        <!-- 默认只加载最近一批，还有更早的历史时给出入口 -->
                        <div v-if="hasMoreMessages" class="load-earlier">
                            <el-button text size="small" :loading="loadingMoreMessages" @click="loadEarlierMessages">
                                加载更早的消息
                            </el-button>
                        </div>
                        <el-empty v-if="!loadingMessages && sessionMessages.length === 0" description="暂无对话记录" />
                        <div v-for="message in sessionMessages" :key="message.id" class="message-item" :class="message.senderType === 1 ? 'user-message' : 'ai-message'">
                            <div class="message-header">
                               <span class="sender">{{ message.senderType === 1 ? '用户' : 'AI助手' }}</span>
                               <span class="time">{{ formatDateTime(message.createdAt) }}</span>
                            </div>
                            <div class="message-content">{{ message.content }}</div>
                        </div>
                    </div>
                </div>
            </div>
            <template #footer>
                <el-button @click="showDetailDialog = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup>
import { onMounted, ref, reactive, computed, nextTick } from 'vue'
import PageHead from '@/components/backend/PageHead.vue'
import TableSearch from '@/components/backend/TableSearch.vue'
import { getSessionPage, getSessionMessages, updateSessionTitle, deleteSession } from '@/api/consultation'
import { ElMessageBox, ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/format'
// 情绪映射统一收敛到 utils，避免与「情绪日志」页各存一份后漂移
import { getEmotionTagType, getEmotionScoreColor, riskLevelText, getRiskTagType, parseEmotionAnalysis } from '@/utils/emotion'

// 查询条件：userId 供管理员把范围缩小到指定用户；emotionTag 按情绪分析结果模糊匹配
const formItem = ref([
    { comp: 'input', prop: 'userId', label: '用户ID', placeholder: '请输入用户ID' },
    { comp: 'input', prop: 'emotionTag', label: '情绪', placeholder: '如：开心 / 焦虑 / 悲伤' }
])

const tableData = ref([])
const loading = ref(false)

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

// 保存最近一次查询条件，翻页时沿用，避免丢失筛选
const queryForm = reactive({})

// 会话 ID -> 解析后的情绪分析结果，避免模板里对同一行重复 JSON.parse
const emotionMap = ref({})

const handleSearch = async (formData) => {
    // 表单触发查询时才覆盖条件并回到第一页；翻页传 undefined，沿用上次条件
    if (formData) {
        Object.assign(queryForm, formData)
        pagination.currentPage = 1
    }

    const params = {
        currentPage: pagination.currentPage,
        size: pagination.size
    }
    // 空值不拼进参数：后端 userId 是 Long，传空字符串会绑定失败；emotionTag 传空串会变成 LIKE '%%'
    if (queryForm.userId) params.userId = queryForm.userId
    if (queryForm.emotionTag) params.emotionTag = queryForm.emotionTag

    loading.value = true
    try {
        const { records, total } = await getSessionPage(params)
        const list = records || []
        tableData.value = list
        pagination.total = total || 0

        const map = {}
        list.forEach(row => {
            map[row.id] = parseEmotionAnalysis(row.lastEmotionAnalysis)
        })
        emotionMap.value = map
    } catch (e) {
        // 失败提示已由响应拦截器统一弹出，这里仅保证列表回到可控状态
        tableData.value = []
        emotionMap.value = {}
        pagination.total = 0
    } finally {
        loading.value = false
    }
}

const handleChange = (page) => {
    pagination.currentPage = page
    handleSearch()
}

// 会话详情
const sessionDetail = ref({})
const sessionMessages = ref([])
const loadingMessages = ref(false)
const showDetailDialog = ref(false)

// ===== 消息分页（向前加载更早的历史）=====
// 后端默认只返回最近 50 条，靠 beforeId 游标向前翻，避免超长会话一次性全读进内存
const MESSAGE_PAGE_SIZE = 50
const hasMoreMessages = ref(false)
// 当前已加载的「最早一条消息」的库内 id，作为下一次向前翻页的游标
const oldestMessageId = ref(null)
const loadingMoreMessages = ref(false)
const messagesListRef = ref(null)

const resetMessagePaging = () => {
    hasMoreMessages.value = false
    oldestMessageId.value = null
    loadingMoreMessages.value = false
}

// 加载最近一批消息
const loadSessionMessages = async (sessionId) => {
    const res = await getSessionMessages(sessionId, { limit: MESSAGE_PAGE_SIZE })
    const records = res?.records || []
    sessionMessages.value = records
    hasMoreMessages.value = !!res?.hasMore
    // 列表按时间升序，首条即最早的一条
    oldestMessageId.value = records.length ? records[0].id : null
}

// 向前加载更早的历史：以当前最早一条消息 id 为游标，结果插到列表头部
const loadEarlierMessages = async () => {
    const sessionId = sessionDetail.value?.id
    if (!sessionId || oldestMessageId.value === null || loadingMoreMessages.value) return

    loadingMoreMessages.value = true
    const container = messagesListRef.value
    const prevScrollHeight = container?.scrollHeight || 0
    const prevScrollTop = container?.scrollTop || 0
    try {
        const res = await getSessionMessages(sessionId, {
            limit: MESSAGE_PAGE_SIZE,
            beforeId: oldestMessageId.value
        })
        const earlier = res?.records || []
        if (earlier.length) {
            sessionMessages.value = [...earlier, ...sessionMessages.value]
            oldestMessageId.value = earlier[0].id
        }
        hasMoreMessages.value = !!res?.hasMore
        // 等 DOM 更新完再按新增高度下移视野，避免插入后视野跳到最顶部
        await nextTick()
        if (container) {
            container.scrollTop = prevScrollTop + (container.scrollHeight - prevScrollHeight)
        }
    } catch (e) {
        // 失败提示已由响应拦截器统一弹出
    } finally {
        loadingMoreMessages.value = false
    }
}

// 详情里可展示的情绪分析结果，为空时整块换成空状态提示
const detailEmotion = computed(() => parseEmotionAnalysis(sessionDetail.value.lastEmotionAnalysis))

const viewSessionDetail = async (row) => {
    sessionDetail.value = row
    sessionMessages.value = []
    resetMessagePaging()
    showDetailDialog.value = true
    loadingMessages.value = true
    try {
        await loadSessionMessages(row.id)
    } catch (e) {
        sessionMessages.value = []
        resetMessagePaging()
    } finally {
        loadingMessages.value = false
    }
}

const handleDetailClosed = () => {
    sessionMessages.value = []
    sessionDetail.value = {}
    resetMessagePaging()
}

// 改名
const handleRename = (row) => {
    ElMessageBox.prompt('请输入新的会话标题', '修改会话标题', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: row.sessionTitle || '',
        // 与后端 ConsultationSessionTitleUpdateDTO 的 @NotBlank + @Size(max=50) 对齐，避免白跑一趟请求
        inputValidator: (value) => {
            if (!value || !value.trim()) return '标题不能为空'
            if (value.trim().length > 50) return '标题长度不能超过 50 个字符'
            return true
        },
        inputErrorMessage: '标题不合法'
    }).then(({ value }) => {
        updateSessionTitle(row.id, value.trim()).then(() => {
            ElMessage.success('修改成功')
            handleSearch()
        }).catch(() => {
            // 失败由拦截器提示
        })
    }).catch(() => {
        // 取消输入，无需处理
    })
}

// 删除（会话下的消息由后端级联删除）
const handleDelete = (row) => {
    ElMessageBox.confirm(
        `确认删除会话「${row.sessionTitle || '#' + row.id}」吗？该会话下的所有对话记录将一并删除，且不可恢复。`,
        '删除确认',
        {
            confirmButtonText: '确认删除',
            cancelButtonText: '取消',
            type: 'danger'
        }
    ).then(() => {
        deleteSession(row.id).then(() => {
            ElMessage.success('删除成功')
            // 删掉的是当前页最后一条时回退一页，避免停在空白页
            if (tableData.value.length === 1 && pagination.currentPage > 1) {
                pagination.currentPage -= 1
            }
            handleSearch()
        }).catch(() => {
            // 失败由拦截器提示
        })
    }).catch(() => {
        // 取消删除，无需处理
    })
}

onMounted(() => {
    handleSearch()
})
</script>

<style lang="scss" scoped>
    .user-cell {
        display: flex;
        flex-direction: column;
        line-height: 1.4;

        .user-name {
            color: #333;
        }

        .user-id {
            font-size: 12px;
            color: #909399;
        }
    }

    .text-muted {
        color: #909399;
        font-size: 13px;
    }

    .session-title {
        font-weight: 500;
        color: #333;
        margin-bottom: 4px;
    }

    .session-preview {
        font-size: 13px;
        color: #666;
        margin-bottom: 4px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }

    .session-detail {
        max-height: 70vh;
        overflow-y: auto;

        .detail-header {
            margin-bottom: 20px;
            padding: 16px;
            background: #f8f9fa;
            border-radius: 8px;
            border: 1px solid #e9ecef;
        }

        .detail-row {
            display: flex;
            align-items: center;
            margin-bottom: 8px;

            &:last-child {
                margin-bottom: 0;
            }

            .detail-label {
                font-weight: 500;
                color: #495057;
                min-width: 80px;
                margin-right: 8px;
            }

            .detail-value {
                color: #333;
            }
        }

        .emotion-block {
            margin-bottom: 20px;

            .block-header {
                display: flex;
                align-items: baseline;
                justify-content: space-between;
                margin-bottom: 12px;

                h4 {
                    margin: 0;
                    color: #333;
                    font-size: 16px;
                    font-weight: 500;
                }

                .block-time {
                    font-size: 12px;
                    color: #909399;
                }
            }

            .keyword-tag {
                margin-right: 6px;
                margin-bottom: 4px;
            }

            .improvement-list {
                margin: 0;
                padding-left: 18px;
                color: #333;

                li {
                    line-height: 1.8;
                }
            }
        }

        .emotion-empty {
            margin-bottom: 20px;
        }
    }

    .messages-container {
        margin-top: 20px;

        .messages-header {
            margin-bottom: 16px;

            h4 {
                margin: 0;
                color: #333;
                font-size: 16px;
                font-weight: 500;
            }
        }

        .messages-list {
            max-height: 400px;
            overflow-y: auto;
            border: 1px solid #e9ecef;
            border-radius: 8px;
            padding: 16px;
            background: #fff;

            // 「加载更早」入口：置于消息流顶部，无更多历史时整个节点不渲染
            .load-earlier {
                display: flex;
                justify-content: center;
                margin-bottom: 8px;
            }

            .message-item {
                margin-bottom: 12px;
                padding: 12px;
                border-radius: 8px;
                background: #f8f9fa;
                border: 1px solid #e9ecef;

                &:last-child {
                    margin-bottom: 0;
                }

                &.user-message {
                    background: #e8f4fd;
                }

                &.ai-message {
                    background: #f0f9f0;
                }
            }

            .message-header {
                display: flex;
                justify-content: space-between;
                align-items: center;

                .sender {
                    font-weight: 500;
                    color: #333;
                    display: flex;
                    align-items: center;
                    gap: 4px;
                }

                .time {
                    font-size: 12px;
                    color: #999;
                }
            }

            .message-content {
                color: #333;
                line-height: 1.6;
                white-space: pre-wrap;
                word-break: break-word;
                margin-top: 8px;
                font-size: 14px;
            }
        }
    }
</style>
