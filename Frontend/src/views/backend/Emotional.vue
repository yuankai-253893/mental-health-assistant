<template>
    <div>
        <PageHead title="情绪日志" />
        <TableSearch :formItem="formItem" @search="handleSearch" />
        <el-table :data="tableData" class="log-table" style="width: 100%">
            <el-table-column prop="userId" label="用户ID" width="80" />
            <el-table-column label="用户名" width="120">
                <template #default="scope">
                    <span>{{ scope.row.username || '-' }}</span>
                </template>
            </el-table-column>
            <el-table-column prop="diaryDate" label="记录日期" width="120" />
            <el-table-column label="情绪评分" width="220">
                <template #default="scope">
                    <el-rate :model-value="scope.row.moodScore" :max="10" disabled />
                </template>
            </el-table-column>
            <el-table-column label="生活指标" width="120">
                <template #default="scope">
                    <div>
                        <p>
                            睡眠：{{ scope.row.sleepQuality }} / 5
                        </p>
                        <p>
                            压力：{{ scope.row.stressLevel }} / 5
                        </p>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="emotionTriggers" label="情绪触发因素" width="120" />
            <el-table-column prop="diaryContent" label="日记内容" width="250" />
            <el-table-column label="AI分析" width="150">
                <template #default="scope">
                    <el-tag v-if="scope.row.aiEmotionAnalysis" type="success" size="small">已分析</el-tag>
                    <el-tag v-else type="info" size="small">未分析</el-tag>
                    <div class="ai-analysis-preview" v-if="getAiSummary(scope.row)">
                        {{ getAiSummary(scope.row) }}
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="360" fixed="right">
                <template #default="scope">
                    <el-button @click="viewSessionDetail(scope.row)" text type="primary">详情</el-button>
                    <el-button @click="handleAnalyze(scope.row)" text type="warning"
                        :loading="analyzingId === scope.row.id">AI分析</el-button>
                    <el-button @click="openTaskDialog(scope.row)" text>任务记录</el-button>
                    <el-button @click="handleDelete(scope.row)" text type="danger">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 25px" v-model:current-page="pagination.currentPage" :page-size="pagination.size"
            layout="prev, pager, next" :total="pagination.total" @current-change="handleChange" />

        <el-dialog v-model="detailDialogVisible" title="情绪日志详情" width="800px" :close-on-click-modal="false">
            <div class="detail-content" v-if="currentDetail">
                <div class="detail-section">
                    <h4>用户信息</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="用户名">{{ currentDetail.username }}</el-descriptions-item>
                        <el-descriptions-item label="昵称">{{ currentDetail.nickname }}</el-descriptions-item>
                        <el-descriptions-item label="用户ID">{{ currentDetail.userId }}</el-descriptions-item>
                        <el-descriptions-item label="记录日期">{{ currentDetail.diaryDate }}</el-descriptions-item>
                    </el-descriptions>
                </div>
                <div class="detail-section">
                    <h4>情绪状态</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="情绪评分">
                            <el-rate :model-value="currentDetail.moodScore" :max="10" disabled />
                        </el-descriptions-item>
                        <el-descriptions-item label="主要情绪">
                            <el-tag :type="getEmotionTagType(currentDetail.dominantEmotion)">{{
                                currentDetail.dominantEmotion ||
                                '-' }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="睡眠质量">{{ currentDetail.sleepQuality || '-'
                            }}/5</el-descriptions-item>
                        <el-descriptions-item label="压力水平">{{ currentDetail.stressLevel || '-'
                            }}/5</el-descriptions-item>
                    </el-descriptions>
                </div>
                <div class="detail-section">
                    <h4>日记内容</h4>
                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="情绪触发因素">{{ currentDetail.emotionTriggers || '无'
                            }}</el-descriptions-item>
                        <el-descriptions-item label="日记内容">{{ currentDetail.diaryContent || '无'
                            }}</el-descriptions-item>
                    </el-descriptions>
                </div>
                <div class="detail-section">
                    <h4>AI情绪分析结果</h4>
                    <div class="ai-analysis-result" v-if="hasAiAnalysis">
                        <el-descriptions :column="2" border>
                            <el-descriptions-item label="主要情绪">
                                <el-tag :type="getEmotionTagType(aiData.primaryEmotion)">{{
                                    aiData.primaryEmotion}}</el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="情绪积极度">
                                <el-progress :percentage="aiData.emotionScore"
                                    :color="getEmotionScoreColor(aiData.emotionScore)" :stroke-width="8" />
                            </el-descriptions-item>
                            <el-descriptions-item label="风险等级">
                                <el-tag :type="getRiskTagType(aiData.riskLevel)">
                                    {{ aiData.riskDescription || riskLevelText(aiData.riskLevel) }}
                                </el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="情绪性质">
                                <el-tag :type="aiData.isNegative ? 'danger' : 'success'">{{ aiData.isNegative ? '负面情绪' :
                                    '正面情绪'}}</el-tag>
                            </el-descriptions-item>
                        </el-descriptions>
                        <div class="ai-suggestion-section">
                            <h5>专业建议</h5>
                            <div class="suggestion-content">{{ aiData.suggestion || '无' }}</div>
                        </div>
                        <div class="ai-risk-section">
                            <h5>风险描述</h5>
                            <div class="risk-content">{{ aiData.riskDescription || '无' }}</div>
                        </div>
                        <div class="ai-improvements-section">
                            <h5>改善建议</h5>
                            <ul class="improvement-list">
                                <li v-for="item in aiData.improvementSuggestions" :key="item">{{ item }}</li>
                            </ul>
                        </div>
                    </div>
                    <el-empty v-else description="暂无AI分析结果" :image-size="60" />

                </div>
                <div class="detail-section">
                    <h4>时间信息</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="创建时间">{{ formatDateTime(currentDetail.createdAt) }}</el-descriptions-item>
                        <el-descriptions-item label="更新时间">{{ formatDateTime(currentDetail.updatedAt) }}</el-descriptions-item>
                    </el-descriptions>
                </div>
            </div>
            <template #footer>
                <el-button @click="detailDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>

        <!-- AI 分析任务记录 -->
        <el-dialog v-model="taskDialogVisible" title="AI 分析任务记录" width="760px" :close-on-click-modal="false">
            <div class="task-filter">
                <el-select v-model="taskStatus" placeholder="全部状态" clearable style="width: 180px"
                    @change="handleTaskStatusChange">
                    <el-option label="全部" value="" />
                    <el-option label="待处理" value="PENDING" />
                    <el-option label="处理中" value="PROCESSING" />
                    <el-option label="已完成" value="COMPLETED" />
                    <el-option label="失败" value="FAILED" />
                </el-select>
            </div>
            <el-table :data="taskList" v-loading="taskLoading" style="width: 100%">
                <el-table-column prop="id" label="任务ID" width="80" />
                <el-table-column prop="taskType" label="来源" width="90">
                    <template #default="scope">{{ typeText(scope.row.taskType) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                    <template #default="scope">
                        <el-tag :type="taskTagType(scope.row.status)" size="small">
                            {{ scope.row.statusText || scope.row.status }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="retryCount" label="重试" width="70" />
                <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
                <el-table-column label="操作" width="100">
                    <template #default="scope">
                        <el-button v-if="scope.row.status === 'FAILED'" text type="primary"
                            :loading="retryingId === scope.row.id" @click="handleRetry(scope.row)">重试</el-button>
                        <span v-else>-</span>
                    </template>
                </el-table-column>
            </el-table>
            <el-empty v-if="!taskLoading && taskList.length === 0" description="暂无分析任务" :image-size="60" />
            <el-pagination style="margin-top: 16px" v-model:current-page="taskPagination.currentPage"
                :page-size="taskPagination.size" layout="prev, pager, next" :total="taskPagination.total"
                @current-change="loadTasks" />
            <template #footer>
                <el-button @click="taskDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import PageHead from '@/components/backend/PageHead.vue'
import TableSearch from '@/components/backend/TableSearch.vue'
import { getEmotionalPage, deleteEmotional, getAnalysisTaskPage, retryAnalysisTask, analyzeDiaryAsAdmin } from '@/api/admin'
import { ElMessageBox, ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/format'
// 情绪映射与查询条件判断统一收敛到 utils，避免与「咨询记录」页各存一份后漂移
import { getEmotionTagType, getEmotionScoreColor, riskLevelText, getRiskTagType } from '@/utils/emotion'
import { isFilled } from '@/utils/query'

const formItem = [
    { comp: 'input', prop: 'userId', label: '用户ID', placeholder: '请输入用户ID' },
    // 后端 dominantEmotion 走 like 匹配，用输入框比固定下拉更灵活（情绪词种类多）
    { comp: 'input', prop: 'dominantEmotion', label: '主要情绪', placeholder: '如：焦虑 / 开心' },
    {
        comp: 'select', prop: 'moodScoreRange', label: '情绪评分', placeholder: '请选择评分范围', options: [{
            label: '低分（1-3）',
            value: '1-3'
        }, {
            label: '中分（4-6）',
            value: '4-6'
        }, {
            label: '高分（7-10）',
            value: '7-10'
        }]
    }
]


// 列表
const tableData = ref([])
// 分页参数
const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

const handleChange = (page) => {
    pagination.currentPage = page
    handleSearch()
}

// 保存最近一次查询条件，翻页时沿用，避免丢失筛选
const queryForm = reactive({})

const handleSearch = async (formData) => {
    if (formData) {
        Object.assign(queryForm, formData)
        pagination.currentPage = 1
    }

    const { moodScoreRange, ...rest } = queryForm
    const params = {
        currentPage: pagination.currentPage,
        size: pagination.size
    }
    if (isFilled(rest.userId)) params.userId = rest.userId
    if (isFilled(rest.dominantEmotion)) params.dominantEmotion = rest.dominantEmotion

    // 评分区间字符串（如 "1-3"）转换为后端支持的 minMoodScore / maxMoodScore
    if (moodScoreRange) {
        const [min, max] = moodScoreRange.split('-').map(Number)
        params.minMoodScore = min
        params.maxMoodScore = max
    }

    try {
        const { records, total } = await getEmotionalPage(params)
        tableData.value = records || []
        pagination.total = total || 0
    } catch (e) {
        // 业务/网络错误在拦截器统一提示，这里保证列表回到可控状态
        tableData.value = []
        pagination.total = 0
    }
}

// 详情
const detailDialogVisible = ref(false)
const currentDetail = ref(null)
const aiData = ref(null)
const viewSessionDetail = (row) => {
    currentDetail.value = row
    aiData.value = {}
    if (row.aiEmotionAnalysis) {
        try {
            aiData.value = JSON.parse(row.aiEmotionAnalysis)
        } catch (e) {
            // 解析失败时保持空对象，界面显示“暂无AI分析结果”
            aiData.value = {}
        }
    }
    detailDialogVisible.value = true
}

// 是否存在可展示的 AI 分析结果，为空时整块隐藏，避免渲染出 undefined/0 的空壳
const hasAiAnalysis = computed(() => aiData.value && Object.keys(aiData.value).length > 0)

// 删除
const handleDelete = (row) => {
    ElMessageBox.confirm('确定删除该条记录吗？', '删除确认', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'danger'
    }).then(() => {
        // 确认删除
        deleteEmotional(row.id).then(() => {
            handleSearch()
        }).catch(() => {
            // 删除失败由拦截器提示，无需额外处理
        })
    })
}

// 列表里附带展示一行 AI 结论摘要，解析失败时静默返回空字符串
const getAiSummary = (row) => {
    if (!row || !row.aiEmotionAnalysis) return ''
    try {
        const data = JSON.parse(row.aiEmotionAnalysis)
        return [data.primaryEmotion, data.riskLevel ? `风险：${data.riskLevel}` : '']
            .filter(Boolean)
            .join(' · ')
    } catch (e) {
        return ''
    }
}

// 管理端为指定日记发起 AI 分析
const analyzingId = ref(null)
const handleAnalyze = async (row) => {
    analyzingId.value = row.id
    try {
        await analyzeDiaryAsAdmin(row.id)
        ElMessage.success('已提交分析任务，稍后刷新查看结果')
    } catch (e) {
        // 错误提示由请求拦截器统一处理
    } finally {
        analyzingId.value = null
    }
}

// AI 任务记录
const taskDialogVisible = ref(false)
const taskLoading = ref(false)
const taskList = ref([])
const retryingId = ref(null)
const currentTaskDiaryId = ref(null)
const taskStatus = ref('')
const taskPagination = reactive({ currentPage: 1, size: 5, total: 0 })

const typeText = (type) => {
    const map = { AUTO: '自动', MANUAL: '手动', ADMIN: '管理端', BATCH: '批量' }
    return map[type] || type
}

const taskTagType = (status) => {
    const map = { PENDING: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
    return map[status] || 'info'
}

const loadTasks = async () => {
    if (!currentTaskDiaryId.value) return
    taskLoading.value = true
    try {
        const params = {
            currentPage: taskPagination.currentPage,
            size: taskPagination.size,
            diaryId: currentTaskDiaryId.value
        }
        // 状态按需拼接，空值不传（后端 status 为空串会当成无效枚举）
        if (taskStatus.value) {
            params.status = taskStatus.value
        }
        const { records, total } = await getAnalysisTaskPage(params)
        taskList.value = records || []
        taskPagination.total = total || 0
    } catch (e) {
        taskList.value = []
        taskPagination.total = 0
    } finally {
        taskLoading.value = false
    }
}

// 切换状态筛选后回到第一页，避免停留在越界页码看到空白
const handleTaskStatusChange = () => {
    taskPagination.currentPage = 1
    loadTasks()
}

const openTaskDialog = (row) => {
    currentTaskDiaryId.value = row.id
    taskPagination.currentPage = 1
    taskStatus.value = ''
    taskList.value = []
    taskDialogVisible.value = true
    loadTasks()
}

const handleRetry = async (row) => {
    retryingId.value = row.id
    try {
        await retryAnalysisTask(row.id)
        ElMessage.success('已重新入队')
        loadTasks()
    } catch (e) {
        // 错误提示由请求拦截器统一处理
    } finally {
        retryingId.value = null
    }
}

onMounted(() => {
    handleSearch()
})

</script>


<style lang="scss" scoped>
/* 情绪评分满分 10 颗星，默认 18px 图标 + 6px 间距需 264px，
   超出列宽会被单元格的 overflow: hidden 裁掉，这里缩小图标与间距保证 10 颗全显 */
.log-table {
    :deep(.el-rate__icon) {
        font-size: 16px;
        margin-right: 3px;
    }
}

.task-filter {
    margin-bottom: 16px;
}

.detail-content {
    .detail-section {
        margin-bottom: 24px;

        h4 {
            margin: 0 0 16px 0;
            color: #303133;
            font-size: 16px;

            i {
                margin-right: 8px;
                color: #409eff;
            }
        }
    }
}

// AI分析相关样式
.ai-analysis-status {
    .ai-status-tag {
        margin-bottom: 4px;

        i {
            margin-right: 4px;
        }
    }

    .ai-analysis-preview {
        font-size: 11px;
        color: #909399;
        margin-top: 2px;
    }
}

.ai-analysis-result {

    .ai-keywords-section,
    .ai-suggestion-section,
    .ai-risk-section,
    .ai-improvements-section {
        margin-top: 16px;
        padding: 12px;
        background-color: #f8f9fa;
        border-radius: 4px;

        h5 {
            margin: 0 0 8px 0;
            color: #606266;
            font-size: 14px;
            font-weight: 600;

            i {
                margin-right: 6px;
                color: #909399;
            }
        }
    }

    .keywords-container {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;

        .keyword-tag {
            background-color: #e1f3d8;
            color: #67c23a;
            border-color: #b3d8a4;
        }
    }

    .suggestion-content,
    .risk-content {
        line-height: 1.6;
        color: #606266;
        background-color: white;
        padding: 8px;
        border-radius: 4px;
        border: 1px solid #ebeef5;
    }

    .improvement-list {
        margin: 0;
        padding-left: 20px;

        li {
            margin-bottom: 4px;
            color: #606266;
            line-height: 1.5;
        }
    }

    .ai-analysis-meta {
        margin-top: 16px;
        padding-top: 12px;
        border-top: 1px solid #ebeef5;

        .analysis-time {
            margin: 0;
            font-size: 12px;
            color: #909399;

            i {
                margin-right: 4px;
            }
        }
    }

    .el-progress {
        .el-progress__text {
            font-size: 12px !important;
        }
    }
}
</style>
