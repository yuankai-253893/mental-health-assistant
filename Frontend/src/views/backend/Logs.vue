<template>
    <div>
        <PageHead title="操作日志" />
        <TableSearch :formItem="formItem" @search="handleSearch" />
        <el-table :data="tableData" v-loading="loading" style="width: 100%; margin-top: 25px"
            empty-text="暂无操作日志" :row-class-name="rowClassName">
            <el-table-column type="expand">
                <template #default="scope">
                    <div class="log-detail">
                        <div class="detail-item">
                            <span class="detail-label">请求地址：</span>
                            <span class="detail-value">{{ scope.row.requestUrl || '-' }}</span>
                        </div>
                        <div class="detail-item">
                            <span class="detail-label">请求参数：</span>
                            <pre class="detail-code">{{ scope.row.params || '无' }}</pre>
                        </div>
                        <div v-if="scope.row.status === 0" class="detail-item">
                            <span class="detail-label">异常信息：</span>
                            <pre class="detail-code error-code">{{ scope.row.errorMsg || '无' }}</pre>
                        </div>
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="操作时间" width="170">
                <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作用户" width="140">
                <template #default="scope">
                    <span>{{ scope.row.username || '匿名' }}</span>
                    <span v-if="scope.row.userId" class="user-id">（ID：{{ scope.row.userId }}）</span>
                </template>
            </el-table-column>
            <el-table-column prop="operation" label="操作" width="150" />
            <el-table-column label="方法" width="90">
                <template #default="scope">
                    <el-tag size="small" effect="plain" :type="methodTagType(scope.row.method)">
                        {{ scope.row.method }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="requestUrl" label="请求地址" min-width="220" show-overflow-tooltip />
            <el-table-column label="耗时" width="100">
                <template #default="scope">{{ scope.row.costTime ?? 0 }} ms</template>
            </el-table-column>
            <el-table-column label="结果" width="90">
                <template #default="scope">
                    <el-tag size="small" :type="scope.row.status === 1 ? 'success' : 'danger'">
                        {{ scope.row.status === 1 ? '成功' : '失败' }}
                    </el-tag>
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
    </div>
</template>
<script setup>
import { onMounted, ref, reactive } from 'vue'
import PageHead from '@/components/backend/PageHead.vue'
import TableSearch from '@/components/backend/TableSearch.vue'
import { getOperationLogPage } from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const formItem = ref([
    { comp: 'input', prop: 'username', label: '操作用户', placeholder: '请输入用户名' },
    { comp: 'input', prop: 'operation', label: '操作描述', placeholder: '如：新增知识文章' },
    {
        comp: 'select', prop: 'status', label: '执行结果', placeholder: '请选择结果', options: [
            { label: '成功', value: 1 },
            { label: '失败', value: 0 }
        ]
    },
    {
        comp: 'daterange',
        prop: 'dateRange',
        label: '操作时间',
        // 后端接收 startDate / endDate 两个独立参数，这里用区间选择器组合
        attrs: {
            type: 'daterange',
            valueFormat: 'YYYY-MM-DD',
            startPlaceholder: '开始日期',
            endPlaceholder: '结束日期',
            rangeSeparator: '至',
            unlinkPanels: true
        }
    }
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

// 只拼接真正填写过的条件。注意不能用真值判断：status 允许取 0（失败）
const isFilled = (val) => val !== '' && val !== null && val !== undefined

const methodTagType = (method) => {
    const map = { POST: 'primary', PUT: 'warning', DELETE: 'danger' }
    return map[method] || 'info'
}

// 失败记录整行标红，审计时一眼能看到异常操作
const rowClassName = ({ row }) => (row.status === 0 ? 'row-failed' : '')

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
    if (isFilled(queryForm.username)) params.username = queryForm.username
    if (isFilled(queryForm.operation)) params.operation = queryForm.operation
    if (isFilled(queryForm.status)) params.status = queryForm.status

    const range = queryForm.dateRange
    if (Array.isArray(range) && range.length === 2 && range[0] && range[1]) {
        params.startDate = range[0]
        params.endDate = range[1]
    }

    loading.value = true
    try {
        const { records, total } = await getOperationLogPage(params)
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

onMounted(() => {
    handleSearch()
})
</script>

<style lang="scss" scoped>
    .user-id {
        font-size: 12px;
        color: #909399;
    }

    .log-detail {
        padding: 8px 16px;

        .detail-item {
            margin-bottom: 10px;

            &:last-child {
                margin-bottom: 0;
            }
        }

        .detail-label {
            color: #606266;
            font-weight: 500;
        }

        .detail-value {
            color: #333;
            word-break: break-all;
        }

        .detail-code {
            margin: 6px 0 0;
            padding: 10px;
            background: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 6px;
            color: #333;
            font-size: 12px;
            line-height: 1.6;
            white-space: pre-wrap;
            word-break: break-all;
            max-height: 220px;
            overflow-y: auto;
        }

        .error-code {
            background: #fef0f0;
            border-color: #fde2e2;
            color: #f56c6c;
        }
    }

    // el-table 的行是渲染在内部 table 上的，scoped 样式需要穿透
    :deep(.row-failed) {
        --el-table-tr-bg-color: #fef0f0;
    }
</style>
