<template>
    <div class="emotionDiary-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width: 60px;height: 60px"></el-image>
                <h1>情绪日志</h1>
            </div>
        </div>
        <div class="content">
            <!-- 情绪评分 -->
            <div class="diary-card">
                <div class="title">今日情绪评分</div>
                <div class="section">
                    <p>您今天的整体情绪状态如何？(1-10分)</p>
                    <div class="rate">
                        <el-rate 
                            v-model="diaryForm.moodScore"
                            :texts="emotionStatus"
                            show-text
                            :max="10"
                            size="large"
                        />
                    </div>
                </div>
            </div>
            <!-- 主要情绪 -->
            <div class="diary-card">
                <div class="title">主要情绪</div>
                <div class="emotion-grid">
                    <div v-for="emotion in emotionOptions" :key="emotion.name" class="emotion-card" :class="{'selected': emotion.name === diaryForm.dominantEmotion}" @click="selectEmotion(emotion.name)">
                        <el-image :src="emotion.url" style="width: 50px;height: 50px"></el-image>
                        <div class="emotion-name">{{emotion.name}}</div>
                    </div>
                </div>
            </div>
            <!-- 详细记录 -->
            <div class="diary-card">
                <div class="title">详细记录</div>
                <div class="detail-form">
                    <div class="form-group">
                        <div class="form-label">情绪触发因素</div>
                        <el-input v-model="diaryForm.emotionTriggers" placeholder="今天什么事情影响了您的情绪？" type="textarea" :rows="3" maxlength="1000" show-word-limit></el-input>
                    </div>
                     <div class="form-group">
                        <div class="form-label">今日感想</div>
                        <el-input v-model="diaryForm.diaryContent" placeholder="写下您今天的想法、感受或发生的有趣事情..." type="textarea" :rows="5" maxlength="2000" show-word-limit></el-input>
                    </div>
                    <!-- 生活指标 -->
                    <div class="life-indicators">
                        <div class="indicator-group">
                            <div class="form-label">睡眠质量</div>
                           <el-select v-model="diaryForm.sleepQuality" placeholder="请选择">
                                <el-option label="很差" :value="1"></el-option>
                                <el-option label="较差" :value="2"></el-option>
                                <el-option label="一般" :value="3"></el-option>
                                <el-option label="良好" :value="4"></el-option>
                                <el-option label="优秀" :value="5"></el-option>
                            </el-select>
                        </div>
                        <div class="indicator-group">
                            <div class="form-label">压力水平</div>
                            <el-select v-model="diaryForm.stressLevel" placeholder="请选择">
                                <el-option label="很低" :value="1"></el-option>
                                <el-option label="较低" :value="2"></el-option>
                                <el-option label="中等" :value="3"></el-option>
                                <el-option label="较高" :value="4"></el-option>
                                <el-option label="很高" :value="5"></el-option>
                            </el-select>
                        </div>
                    </div>
                    <div class="action-buttons">
                        <el-button  @click="resetForm">重置</el-button>
                        <el-button type="primary" :loading="submitting" @click="submit">提交记录</el-button>
                    </div>
                </div>
            </div>
            <!-- AI 情绪分析 -->
            <div class="diary-card">
                <div class="title-row">
                    <div class="title">AI 情绪分析</div>
                    <el-button
                        text
                        type="primary"
                        :loading="analyzing"
                        @click="triggerAnalysis">
                        <el-icon><Refresh /></el-icon>
                        <span>{{ analysisResult ? '重新分析' : '立即分析' }}</span>
                    </el-button>
                </div>

                <!-- 分析进行中 -->
                <div v-if="analyzing" class="analysis-loading">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>AI 正在分析您的情绪状态，请稍候…</span>
                </div>

                <!-- 分析结果 -->
                <div v-else-if="analysisResult" class="analysis-result">
                    <div class="emotion-summary">
                        <div class="emotion-icon">{{ analysisResult.icon || '🙂' }}</div>
                        <div class="emotion-main">
                            <div class="emotion-label">{{ analysisResult.label || analysisResult.primaryEmotion }}</div>
                            <div class="emotion-score">情绪指数 {{ analysisResult.emotionScore ?? '--' }}</div>
                        </div>
                        <el-tag :type="riskTagType" effect="dark">{{ analysisResult.riskDescription || '情绪状态' }}</el-tag>
                    </div>

                    <div class="keywords" v-if="analysisResult.keywords?.length">
                        <el-tag
                            v-for="keyword in analysisResult.keywords"
                            :key="keyword"
                            effect="plain"
                            size="small"
                            class="keyword-tag">{{ keyword }}</el-tag>
                    </div>

                    <div class="suggestion" v-if="analysisResult.suggestion">
                        <div class="suggestion-icon">💝</div>
                        <p>{{ analysisResult.suggestion }}</p>
                    </div>

                    <div class="improvement" v-if="analysisResult.improvementSuggestions?.length">
                        <div class="improvement-title">改善建议</div>
                        <ul class="improvement-list">
                            <li v-for="(item, index) in analysisResult.improvementSuggestions" :key="index">{{ item }}</li>
                        </ul>
                    </div>
                </div>

                <!-- 尚未分析 -->
                <div v-else class="analysis-empty">
                    提交今日情绪记录后，AI 会自动分析并给出建议
                </div>
            </div>
        </div>
    </div>
</template>
<script setup>
    import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
    import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
    import { createOrUpdateEmotionDiary, getTodayEmotionDiary, triggerDiaryAnalysis, getDiaryAnalysisTask } from '@/api/user'
    import { Refresh, Loading } from '@element-plus/icons-vue'

    // 情绪评分
    const emotionStatus = ['绝望崩溃', '消沉抑郁', '焦虑烦躁', '低落不悦', '平静淡然', '轻松惬意', '愉悦舒心', '欢欣满足', '兴奋欣喜', '极致幸福']

    // 情绪选项
    const emotionOptions = [
        { name: '开心', url: new URL('@/assets/images/开心.png', import.meta.url).href },
        { name: '平静', url: new URL('@/assets/images/平静.png', import.meta.url).href },
        { name: '焦虑', url: new URL('@/assets/images/焦虑.png', import.meta.url).href },
        { name: '悲伤', url: new URL('@/assets/images/悲伤.png', import.meta.url).href },
        { name: '兴奋', url: new URL('@/assets/images/兴奋.png', import.meta.url).href },
        { name: '疲惫', url: new URL('@/assets/images/疲惫.png', import.meta.url).href },
        { name: '惊讶', url: new URL('@/assets/images/惊讶.png', import.meta.url).href },
        { name: '困惑', url: new URL('@/assets/images/困惑.png', import.meta.url).href },
    ]

    const selectEmotion = (emotion) => {
        diaryForm.dominantEmotion = emotion
    }

    const diaryForm = reactive({    
        diaryDate: dayjs().format('YYYY-MM-DD'),
        moodScore: null,
        dominantEmotion: '',
        emotionTriggers: '',
        diaryContent: '',
        sleepQuality: null,
        stressLevel: null
    })

    // 重置：会清空已填写的情绪、触发因素与日记正文，先二次确认避免误点丢失内容
    const resetForm = () => {
        ElMessageBox.confirm('确定要重置表单吗？已填写的内容将被清空。', '重置确认', {
            confirmButtonText: '确定重置',
            cancelButtonText: '取消',
            type: 'warning'
        }).then(() => {
            Object.assign(diaryForm, {
                diaryDate: dayjs().format('YYYY-MM-DD'),
                moodScore: null,
                dominantEmotion: '',
                emotionTriggers: '',
                diaryContent: '',
                sleepQuality: null,
                stressLevel: null
            })
        }).catch(() => {
            // 用户取消重置，保留已填写内容
        })
    }

    // 用服务端返回的记录回显表单
    const fillForm = (data) => {
        if (!data) return
        Object.assign(diaryForm, {
            diaryDate: data.diaryDate || dayjs().format('YYYY-MM-DD'),
            moodScore: data.moodScore ?? null,
            dominantEmotion: data.dominantEmotion || '',
            emotionTriggers: data.emotionTriggers || '',
            diaryContent: data.diaryContent || '',
            sleepQuality: data.sleepQuality ?? null,
            stressLevel: data.stressLevel ?? null
        })
    }

    // ==================== AI 情绪分析 ====================

    // 当前日记ID：分析任务以日记为维度，没有日记就无从分析
    const diaryId = ref(null)
    // 分析结果（解析自日记的 aiEmotionAnalysis 字段）
    const analysisResult = ref(null)
    // 是否有分析任务正在执行
    const analyzing = ref(false)

    let pollTimer = null
    let pollAttempts = 0

    // 轮询上限：2 秒一次、最多 30 次（约 60 秒），避免长时间占用页面
    const POLL_INTERVAL = 2000
    const MAX_POLL_ATTEMPTS = 30

    const riskTagType = computed(() => {
        const level = analysisResult.value?.riskLevel
        if (level >= 3) return 'danger'
        if (level >= 2) return 'warning'
        if (level >= 1) return 'info'
        return 'success'
    })

    const stopPolling = () => {
        if (pollTimer) {
            clearInterval(pollTimer)
            pollTimer = null
        }
        analyzing.value = false
    }

    // 后端把分析结果以 JSON 字符串存库，这里兼容「字符串」与「已解析对象」两种形态
    const parseAnalysis = (raw) => {
        if (!raw) return null
        if (typeof raw === 'object') return raw
        try {
            return JSON.parse(raw)
        } catch (e) {
            // 历史脏数据解析失败按「暂无分析结果」处理，不让整页崩掉
            return null
        }
    }

    // 重新拉取今日日记，取回最新的分析结果
    const reloadDiary = async () => {
        try {
            const data = await getTodayEmotionDiary()
            if (!data) return
            diaryId.value = data.id
            analysisResult.value = parseAnalysis(data.aiEmotionAnalysis)
        } catch (e) {
            // 静默处理：分析结果拉取失败不影响表单
        }
    }

    // 轮询任务状态，直到完成 / 失败 / 超时
    const startPolling = () => {
        if (!diaryId.value) return
        stopPolling()
        analyzing.value = true
        pollAttempts = 0

        pollTimer = setInterval(async () => {
            pollAttempts++
            if (pollAttempts > MAX_POLL_ATTEMPTS) {
                stopPolling()
                ElMessage.warning('AI 分析耗时较长，请稍后刷新页面查看结果')
                return
            }
            try {
                const task = await getDiaryAnalysisTask(diaryId.value)
                if (!task) return
                if (task.status === 'COMPLETED' || task.status === 'FAILED') {
                    stopPolling()
                    if (task.status === 'FAILED') {
                        ElMessage.error(task.errorMessage || 'AI 分析失败，请稍后重试')
                    } else {
                        await reloadDiary()
                    }
                }
            } catch (e) {
                // 单次轮询失败不打断整体流程，下一轮继续
            }
        }, POLL_INTERVAL)
    }

    // 页面加载时若已有进行中的任务（如刷新页面），继续轮询以恢复进度显示
    const checkRunningTask = async () => {
        if (!diaryId.value) return
        try {
            const task = await getDiaryAnalysisTask(diaryId.value)
            if (task && (task.status === 'PENDING' || task.status === 'PROCESSING')) {
                startPolling()
            }
        } catch (e) {
            // 静默处理
        }
    }

    // 手动触发（重新）分析
    const triggerAnalysis = async () => {
        if (!diaryId.value) {
            ElMessage.warning('请先提交今日情绪记录')
            return
        }
        try {
            await triggerDiaryAnalysis(diaryId.value)
            startPolling()
        } catch (e) {
            // 失败提示已由 request.js 拦截器统一弹出
        }
    }

    // 组件销毁时清掉定时器，避免离开页面后仍在轮询
    onUnmounted(stopPolling)

    // 进入页面时回显今天已提交的记录，未提交则保持空白表单
    const loadTodayDiary = async () => {
        try {
            const data = await getTodayEmotionDiary()
            fillForm(data)
            if (data) {
                diaryId.value = data.id
                analysisResult.value = parseAnalysis(data.aiEmotionAnalysis)
                checkRunningTask()
            }
        } catch (e) {
            // 加载失败提示已由 request.js 拦截器统一弹出，页面保持空白表单
        }
    }

    onMounted(loadTodayDiary)

    // 提交中状态，防止重复提交
    const submitting = ref(false)

    const submit = async () => {
        if (!diaryForm.moodScore) {
            ElMessage.error('请选择情绪评分')
            return
        }
        submitting.value = true
        try {
            const res = await createOrUpdateEmotionDiary(diaryForm)
            ElMessage.success('提交成功')
            // 后端按「用户 + 日期」保存，同一天重复提交是覆盖，回显最新记录而不是清空表单
            fillForm(res)
            if (res) {
                diaryId.value = res.id
                analysisResult.value = parseAnalysis(res.aiEmotionAnalysis)
                // 保存日记时后端会自动入队 AI 分析任务，这里直接开始轮询进度
                startPolling()
            }
        } catch (e) {
            // 失败提示已由 request.js 拦截器统一弹出，这里只需避免未捕获的 Promise 异常
        } finally {
            submitting.value = false
        }
    }

    const iconUrl = new URL('@/assets/images/like.png', import.meta.url).href
</script>


<style lang="scss" scoped>
    .emotionDiary-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    .header-section {
        background: linear-gradient(135deg, #7ED321 0%, #F5A623 100%);
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
        .diary-card {
            margin-bottom: 20px;
            background: white;
            border-radius: 10px;
            padding: 20px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
            .title {
                margin-bottom: 20px;
                font-size: 25px;
                font-weight: 600;
                color: #374151;
            }
            /* 标题与操作按钮同行：按钮推到右侧 */
            .title-row {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 20px;
                .title {
                    margin-bottom: 0;
                }
            }
            .analysis-loading {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 10px;
                padding: 30px 0;
                color: #6b7280;
            }
            .analysis-result {
                .emotion-summary {
                    display: flex;
                    align-items: center;
                    gap: 15px;
                    padding-bottom: 15px;
                    border-bottom: 1px solid #E5E7EB;
                    .emotion-icon {
                        font-size: 40px;
                        line-height: 1;
                    }
                    .emotion-main {
                        flex: 1;
                        min-width: 0;
                        .emotion-label {
                            font-size: 20px;
                            font-weight: 600;
                            color: #374151;
                        }
                        .emotion-score {
                            margin-top: 4px;
                            font-size: 13px;
                            color: #6B7280;
                        }
                    }
                }
                .keywords {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 8px;
                    margin-top: 15px;
                }
                .suggestion {
                    margin-top: 15px;
                    display: flex;
                    gap: 10px;
                    background: #F0FDF4;
                    border-left: 4px solid #7ED321;
                    border-radius: 0 8px 8px 0;
                    padding: 12px 15px;
                    .suggestion-icon {
                        font-size: 18px;
                        line-height: 1.5;
                    }
                    p {
                        color: #374151;
                        line-height: 1.6;
                    }
                }
                .improvement {
                    margin-top: 15px;
                    .improvement-title {
                        margin-bottom: 10px;
                        font-weight: 600;
                        color: #374151;
                    }
                    .improvement-list {
                        padding-left: 20px;
                        li {
                            color: #4B5563;
                            line-height: 1.9;
                        }
                    }
                }
            }
            .analysis-empty {
                padding: 30px 0;
                text-align: center;
                color: #9CA3AF;
            }
            .section {
                margin-bottom: 20px;
                p {
                    font-size: 15px;
                    color: #6B7280;
                    margin-bottom: 15px;
                }
            }
            .emotion-grid {
                display: flex;
                flex-wrap: wrap;
                gap: 10px;
                .emotion-card {
                    padding: 15px;
                    border: 2px solid #E5E7EB;
                    border-radius: 15px;
                    text-align: center;
                    cursor: pointer;
                    background: #F9FAFB;
                    min-width: 100px;
                    .emotion-name {
                        margin-top: 10px;
                        color: #374151;
                    }
                    &.selected {
                        border-color: #7ED321;
                        background: #F0FDF4;
                        transform: translateY(-3px);
                    }
                }
            }
            .detail-form {
                .form-label {
                    margin: 10px 0;
                    color: #374151;
                }
                .life-indicators {
                    display: flex;
                    gap: 20px;
                    .indicator-group {
                        flex: 1;
                    }
                }
                .action-buttons {
                    margin-top: 40px
                }
            }
        }
    }
}
</style>
