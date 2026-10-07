<template>
    <div>
        <PageHead title="咨询记录" />
        <el-table :data="tableData" v-loading="loading" style="width: 100%" empty-text="暂无咨询记录">
            <el-table-column label="会话ID" width="90">
                <template #default="scope">
                    <el-tag size="small" type="info">#{{ scope.row.id }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="用户" width="140">
                <template #default="scope">
                    <span>{{ scope.row.username || '-' }}</span>
                </template>
            </el-table-column>
            <el-table-column label="会话内容">
                <template #default="scope">
                    <div class="session-title">{{ scope.row.sessionTitle }}</div>
                    <div class="session-preview">{{ scope.row.lastMessageContent || '暂无消息' }}</div>
                </template>
            </el-table-column>
            <el-table-column prop="messageCount" label="消息数" width="100" />
            <el-table-column label="最后消息时间" width="170">
                <template #default="scope">
                    <span>{{ formatDateTime(scope.row.lastMessageTime) }}</span>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
                <template #default="scope">
                    <el-button type="primary" text @click="viewSessionDetail(scope.row)">详情</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination
         style="margin-top: 25px"
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
                        <div class="detail-value">{{ sessionDetail.username || '-' }}</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">开始时间：</div>
                        <div class="detail-value">{{ formatDateTime(sessionDetail.startedAt) }}</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">消息数：</div>
                        <div class="detail-value">{{ sessionDetail.messageCount ?? 0 }}</div>
                    </div>
                </div>
                <div class="messages-container">
                    <div class="messages-header">
                        <h4>对话记录</h4>
                    </div>
                    <div class="messages-list" v-loading="loadingMessages">
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
import { onMounted, ref, reactive } from 'vue'
import PageHead from '@/components/backend/PageHead.vue'
import { getConsultationPage, getSessionDetail } from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const tableData = ref([])
const loading = ref(false)

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const handleSearch = async () => {
    loading.value = true
    try {
        const res = await getConsultationPage({
            currentPage: pagination.currentPage,
            size: pagination.size
        })
        const { records, total } = res || {}
        tableData.value = records || []
        pagination.total = total || 0
    } catch (e) {
        // 失败提示已由响应拦截器统一弹出，这里仅保证列表回到可控状态
        tableData.value = []
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

const viewSessionDetail = async (row) => {
    sessionDetail.value = row
    sessionMessages.value = []
    showDetailDialog.value = true
    loadingMessages.value = true
    try {
        sessionMessages.value = await getSessionDetail(row.id) || []
    } catch (e) {
        sessionMessages.value = []
    } finally {
        loadingMessages.value = false
    }
}

const handleDetailClosed = () => {
    sessionMessages.value = []
    sessionDetail.value = {}
}

onMounted(() => {
    handleSearch()
})
</script>

<style lang="scss" scoped>
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
