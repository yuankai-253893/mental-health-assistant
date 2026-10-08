<template>
    <div class="consultation-container">
        <div class="sidebar">
            <!-- AI助手信息 -->
             <div class="ai-assistant-info">
                <div class="breathing-circle">
                    <el-image :src="iconUrl" style="width: 25px;height:25px" alt="AI助手" />
                </div>
                <h3 class="assistant-name">小媛AI助手</h3>
                <div class="online-status">
                    <div class="status-dot"></div>
                    在线服务中
                </div>
             </div>
             <!-- 情绪花园 -->
             <div class="emotion-garden">
                <div class="garden-header">
                    <div class="garden-title"> 情绪花园 </div>
                </div>
                <div class="emotion-info">
                    <div class="emotion-name">{{ currentEmotion.primaryEmotion || '中性' }}</div>
                    <div class="emotion-score">{{ currentEmotion.emotionScore ?? 50 }}</div>
                </div>
                <div class="warm-tips">
                    <div class="emotion-status-text">
                        <span class="status-label">今天感觉</span>
                        <span class="status-emotion">{{ currentEmotion.isNegative ? '需要关注' : '很不错' }}</span>
                    </div>
                    <div class="emotion-intensity">
                        <span class="intensity-dots">
                            <span v-for="dot in 3" :key="dot" class="dot" :class="{'active': getIntensityClass(currentEmotion.emotionScore) >= dot}"></span>
                        </span>
                        <span class="intensity-text">
                            {{ getRiskText(currentEmotion.riskLevel) }}
                        </span>
                    </div>
                    <!-- 温暖建议卡片 -->
                     <div class="warm-suggestion" v-if="currentEmotion.suggestion">
                        <div class="suggestion-icon">💝</div>
                        <div class="suggestion-content">
                            <div class="suggestion-title">给你的小建议</div>
                            <div class="suggestion-text">{{ currentEmotion.suggestion }}</div>
                        </div>
                     </div>
                     <!-- 治愈行动 -->
                      <div class="healing-actions" v-if="currentEmotion.improvementSuggestions?.length > 0">
                        <div class="actions-title">治愈小行动</div>
                        <div class="actions-list">
                            <div v-for="action in currentEmotion.improvementSuggestions" :key="action" class="action-item">
                                <div class="action-icon">✨</div>
                                <div class="action-text">{{ action }}</div>
                            </div>
                        </div>
                      </div>
                      <!-- 风险提示 -->
                    <div class="risk-notice" v-if="currentEmotion.isNegative && currentEmotion.riskLevel > 1">
                        <div class="notice-icon">🤗</div>
                        <div class="notice-content">
                            <div class="notice-title">温馨提示</div>
                            <div class="notice-text">{{ currentEmotion.riskDescription }}</div>
                        </div>
                    </div>
                </div>
             </div>
             <!-- 会话列表 -->
             <div class="session-history">
                <h4 class="section-title">会话列表</h4>
                <div class="session-list">
                    <div v-for="session in sessionList" :key="session.id" @click="handleSessionClick(session)" class="session-item" :class="{ active: currentSession?.sessionId === `session_${session.id}` }">
                        <div class="session-info">
                            <div class="session-title">
                                <el-input
                                    v-if="editingKey === session.id"
                                    v-model="editingTitle"
                                    size="small"
                                    maxlength="50"
                                    class="title-input"
                                    @click.stop
                                    @keydown="handleListTitleKeydown($event, session)"
                                    @blur="saveListTitle(session)" />
                                <span v-else class="title-text" title="点击修改标题"
                                    @click.stop="startEditTitle(session.id, session.sessionTitle, $event)">
                                    {{ session.sessionTitle }}
                                </span>
                                <div class="session-meta">
                                    <span class="session-time">{{ formatDateTime(session.startedAt) }}</span>
                                </div>
                                <div class="session-preview">
                                    {{ session.lastMessageContent }}
                                </div>
                                <div class="session-stats">
                                    <span>
                                        <el-icon>
                                            <ChatRound />
                                        </el-icon>
                                        {{ session.messageCount || 0 }}
                                    </span>
                                    <span>
                                        <el-icon>
                                            <Clock />
                                        </el-icon>
                                        {{ session.durationMinutes || 0 }} 分钟
                                    </span>
                                </div>
                            </div>
                            <div class="session-actions">
                                <el-button text type="danger" size="small" @click.stop="handleDeleteSession(session.id)">
                                    <el-icon>
                                        <DeleteFilled />
                                    </el-icon>
                                </el-button>
                            </div>
                        </div>
                    </div>
                </div>
             </div>
        </div>
        <div class="chat-main">
            <div class="chat-header">
                <div class="header-left">
                    <div class="chat-avatar">
                        <el-image :src="iconUrl1" style="width: 30px;height: 30px" />
                    </div>
                    <div class="chat-info">
                        <h2>小媛AI助手</h2>
                        <p>您的贴心AI心理健康助手</p>
                    </div>
                </div>
                <el-button circle @click="createNewFrontendSession" title="新建会话">
                    <el-icon>
                        <Plus />
                    </el-icon>
                </el-button>
            </div>
            <!-- 会话标题（点击可修改） -->
            <div class="chat-title-bar">
                <el-input
                    v-if="editingKey === 'current'"
                    v-model="editingTitle"
                    size="small"
                    maxlength="50"
                    class="title-input"
                    @keydown="handleCurrentTitleKeydown"
                    @blur="saveCurrentTitle" />
                <span v-else class="title-text" title="点击修改标题"
                    @click="startEditTitle('current', currentSession?.sessionTitle, $event)">
                    {{ currentSession?.sessionTitle || '新对话' }}
                </span>
            </div>
            <!-- 聊天消息区域 -->
            <div class="chat-messages">
                <!-- 欢迎用语 -->
                <div class="message-item ai-message" v-if="messages.length === 0">
                    <div class="message-avatar">
                        <el-image :src="iconUrl" style="width: 18px;height: 18px" />
                    </div>
                    <div class="message-content">
                        <div class="message-bubble">
                            <p>您好！我是小暖，您的AI心理健康助手。很高兴陪伴您，为您提供温暖的心理支持。请告诉我，今天您感觉怎么样？有什么想要分享的吗？</p>
                        </div>
                        <div class="message-time">刚刚</div>
                    </div>
                </div>
                <!-- 消息列表 -->
                <div v-for="msg in messages" :key="msg.id" class="message-item" :class="msg.senderType === 1 ?  'user-message' : 'ai-message'">
                    <div class="message-avatar">
                        <el-image v-if="msg.senderType === 1" style="width: 18px; height:18px" :src="iconUrl2"></el-image>
                        <el-image v-if="msg.senderType === 2" style="width: 18px; height:18px" :src="iconUrl"></el-image>
                    </div>
                    <div class="message-content">
                        <div class="message-bubble">
                            <!-- AI正在思考中 -->
                            <div v-if="msg.senderType === 2 && isAiTyping && !msg.content" class="typing-indicator">
                                <div class="typing-dot"></div>
                                <div class="typing-dot"></div>
                                <div class="typing-dot"></div>
                            </div>
                            <!-- AI错误提示 -->
                            <div v-else-if="msg.isError" class="error-message">
                                <p>{{ msg.content }}</p>
                            </div>
                            <!-- AI正常返回消息 -->
                             <MarkdownRenderer v-else-if="msg.senderType === 2 && !msg.isError" :content="msg.content" :is-ai-message="true" />
                             <!-- 用户消息：纯文本插值渲染（不用 v-html），配合 CSS pre-wrap 保留换行，
                                  防止消息内容被当作 HTML 解析造成存储型 XSS -->
                             <p v-else-if="msg.content" class="user-message-text">{{ msg.content }}</p>
                        </div>
                        <div class="message-time">{{ msg.senderType === 2 && isAiTyping ? '正在输入中...' : formatDateTime(msg.createdAt) }}</div>
                    </div>
                </div>
            </div>
            <!-- 消息输入区域 -->
            <div class="chat-input">
                <div class="input-container">
                    <el-input
                        v-model="userMessage"
                        placeholder="请输入您想要分享的内容..."
                        type="textarea"
                        :rows="3"
                        :disabled="isAiTyping"
                        @keydown="handleKeyDown"
                        class="message-input"
                        clearable />
                        <div class="input-footer">
                            <span>按Enter发送，Shift+Enter换行</span>
                            <span>{{ userMessage.length }}/500</span>
                        </div>
                </div>
                <el-button :disabled="!userMessage.trim() || userMessage.length > 500" type="primary" class="send-btn" @click="sendMessage">
                    <el-icon>
                        <Promotion />
                    </el-icon>
                </el-button>
            </div>
        </div>
    </div>
</template>


<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { startSession, getSessionPage, deleteSession, getSessionMessages, getSessionEmotion, updateSessionTitle } from '@/api/consultation'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatRound, DeleteFilled } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/frontend/MarkdownRenderer.vue'
import { formatDateTime } from '@/utils/format'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import iconUrl from '@/assets/images/robot-fill.png'
import iconUrl1 from '@/assets/images/like.png'
import iconUrl2 from '@/assets/images/users.png'

// 新建会话
const createNewFrontendSession = () => {
    // AI 正在流式回复时不切换，避免回复内容串到新会话
    if (isAiTyping.value) {
        ElMessage.warning('AI正在回复中，请稍候')
        return
    }
    // 创建一个新的会话对象
    const newSession = {
        sessionId: `temp_${Date.now()}`,
        status: 'TEMP',
        sessionTitle: '新对话'
    }
    currentSession.value = newSession
    // 清空当前对话与情绪展示，给出可见的切换反馈
    messages.value = []
    currentEmotion.value = { ...defaultEmotion }
}

// 定义一个当前会话对象
const currentSession = ref(null)
const sessionList = ref([])

// 定义对话消息
const messages = ref([])
// 定义用户输入消息
const userMessage = ref('')
// 定义AI助手是否正在输入
const isAiTyping = ref(false)

// 情绪花园默认值（接口无分析结果时兜底，避免模板取值报错）
const defaultEmotion = {
    primaryEmotion: '中性',
    emotionScore: 50,
    isNegative: false,
    riskLevel: 0,
    suggestion: '情绪状态平稳',
    improvementSuggestions: []
}

const currentEmotion = ref({ ...defaultEmotion })

// 服务端在 SSE 结束后才异步做情绪分析，done 事件到达时结果可能还没落库，
// 因此这里按固定间隔回查若干次，直到 lastEmotionUpdatedAt 发生变化或达到上限。
const EMOTION_POLL_INTERVAL = 1500
const EMOTION_POLL_MAX = 4
let emotionPollTimer = null

const stopEmotionPolling = () => {
    if (emotionPollTimer) {
        clearTimeout(emotionPollTimer)
        emotionPollTimer = null
    }
}

// 拉取一次情绪分析结果，返回该次分析的时间戳（用于判断服务端是否已写入新一轮结果）
const loadSessionEmotion = (sessionId) => {
    // 后端 /session/{sessionId}/emotion 的 sessionId 是 Long，需去掉 SSE 流使用的 "session_" 前缀
    const id = String(sessionId).replace(/^session_/, '')

    return getSessionEmotion(id).then(res => {
        // 接口返回 { sessionId, lastEmotionAnalysis, lastEmotionUpdatedAt }
        // 真正的分析结果在 lastEmotionAnalysis 中，且尚未生成时为 null
        currentEmotion.value = { ...defaultEmotion, ...(res?.lastEmotionAnalysis || {}) }
        return res?.lastEmotionUpdatedAt || null
    }).catch(() => {
        // 情绪分析拉取失败（如尚未生成）时保持默认值，不打断对话
        return null
    })
}

// 刷新当前会话的情绪：先取一次基线，再轮询直到时间戳变化（说明新一轮分析已写回）
const refreshSessionEmotion = async (sessionId) => {
    stopEmotionPolling()
    if (!sessionId) return

    const id = String(sessionId).replace(/^session_/, '')
    const baseline = await loadSessionEmotion(id)

    let attempt = 0
    const poll = async () => {
        attempt += 1
        const latest = await loadSessionEmotion(id)
        if (latest && latest !== baseline) return
        if (attempt < EMOTION_POLL_MAX) {
            emotionPollTimer = setTimeout(poll, EMOTION_POLL_INTERVAL)
        }
    }
    emotionPollTimer = setTimeout(poll, EMOTION_POLL_INTERVAL)
}

const getIntensityClass = (score) => {
    if (score >= 61) {
        return 3
    }
    if (score >= 31) {
        return 2
    }
    return 1
}

const getRiskText = (level) => {
    switch (level) {
        case 0:
            return '正常'
        case 1:
            return '关注'
        case 2:
            return '预警'
        case 3:
            return '危机'
        default:
            return '正常'
    }
}

// 定义处理键盘事件：Enter 发送消息，Shift+Enter 换行
const handleKeyDown = (e) => {
    // 输入法组合输入中（如中文选词时的回车）不触发发送
    if (e.isComposing || e.keyCode === 229) return
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        sendMessage()
    }
}

// 用户发送消息
const sendMessage = () => {
    if (!userMessage.value.trim()) return

    if (isAiTyping.value) {
        ElMessage.error('AI助手正在输入中，请稍后')
        return
    }

    const message = userMessage.value.trim()
    userMessage.value = ''

    // 如果没有会话或者是临时会话，就需要创建一个新的会话
    if (currentSession.value.status === 'TEMP') {
       startNewSession(message)
    } else {
        // 继续现有会话
        messages.value.push({
            id: Date.now(),
            senderType: 1,
            content: message,
            createdAt: new Date().toISOString()
        })
        startAIResponse(currentSession.value.sessionId, message)
    }
}

const startNewSession = (message) => {
    // 构建会话参数
    const sessionParams = {
        initialMessage: message
    }
    if (currentSession.value.sessionTitle === '新对话') {
        sessionParams.sessionTitle = `小媛AI助手 - ${new Date().toLocaleString()}`
    } else {
        // 如果历史会话记录
        sessionParams.sessionTitle = currentSession.value.sessionTitle
    }
    // 调用后端接口创建新会话
    startSession(sessionParams).then(res => {
       // 将后端返回的数据转为前端会话格式
       const sessionData = {
            sessionId: res.sessionId,
            status: res.status,
            sessionTitle: sessionParams.sessionTitle
       }
       // 如果当前是临时会话，更新数据
       if (currentSession.value && currentSession.value.status === 'TEMP') {
            // 更新为正式会话 
            Object.assign(currentSession.value, sessionData)
       } else {
            // 否则，创建一个新的会话
            currentSession.value = sessionData
       }
       // 更新会话列表
       loadSessionList()

       // 添加初始用户消息
       messages.value.push({
        id: Date.now(),
        senderType: 1,
        content: message,
        createdAt: new Date().toISOString()
       })

       // 开始流式对话
       startAIResponse(currentSession.value.sessionId, message)
    }).catch(() => {
        // 创建会话失败已由拦截器统一提示
    })
}

const startAIResponse = (sessionId, userMessage) => {
    // 防止重复发送
    if (isAiTyping.value) {
        ElMessage.error('AI助手正在输入中，请稍后')
        return
    }

    
    isAiTyping.value = true
    // 标记本次流是否正常收到 done，用于避免 onclose 与 done 重复触发情绪刷新
    let streamDone = false

    const aiMessage = {
        id: `ai_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        senderType: 2,
        content: '',
        createdAt: new Date().toISOString()
    }
    messages.value.push(aiMessage)

    // 调用流式接口
    const ctrl = new AbortController() // 用来中止fetch请求
    fetchEventSource('/api/psychological-chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Token': localStorage.getItem('token'),
            'Accept': 'text/event-stream'
        },
        body: JSON.stringify({
            sessionId,
            userMessage
        }),
        signal: ctrl.signal,
        onopen: (response) => {
            if (response.headers.get('Content-Type') !== 'text/event-stream') {
                ElMessage.error('服务器返回非流式数据')
            }
        },
        onmessage: (event) => {
            const raw = event.data.trim()
            if (!raw) return
            const eventName = event.event
            // 当前会话的AI消息
            const aiMessage = messages.value[messages.value.length - 1]

            if (eventName === 'done') {
                isAiTyping.value = false
                streamDone = true
                ctrl.abort()
                // 服务端的情绪分析是异步的，这里轮询等待新一轮结果写回
                refreshSessionEmotion(currentSession.value.sessionId)
                return
            }
            const payload = JSON.parse(raw)
            const ok = String(payload.code) === '200'
            if (ok && payload.data && payload.data.content) {
                aiMessage.content += payload.data.content
            } else if (!ok) {
                // 错误回复的显示
                handleError(payload.message || 'AI回复失败')
            }
        },
        onerror: (err) => {
            handleError(err || 'AI回复失败')
            throw err
        },
        onclose: () => {
            // 正常收尾时 done 分支已经拉起轮询，这里只做兜底：
            // 流被异常中断（未收到 done）时也要刷新一次情绪，避免结果永远不更新
            if (!streamDone) {
                refreshSessionEmotion(currentSession.value.sessionId)
            }
        }
    })

}

// 错误处理函数
const handleError = (error) => {
    // 当前会话的AI消息
    const aiMessage = messages.value[messages.value.length - 1]
    if (aiMessage) {
        aiMessage.content = 'AI回复失败，请重试'
    }
    isAiTyping.value = false
    ElMessage.error('AI回复失败，请重试')
}

const loadSessionList = () => {
    getSessionPage({
        currentPage: 1,
        size: 10
    }).then(res => {
        sessionList.value = res.records
    }).catch(() => {
        // 失败提示已由拦截器统一处理
    })
}

// 获取会话数据
const handleSessionClick = (session) => {
    // 点击会话时，获取会话详情
    getSessionMessages(session.id).then(res => {
        messages.value = res
    }).catch(() => {
        // 失败提示已由拦截器统一处理
    })
    loadSessionEmotion(session.id)
    // 更新当前会话对象数据
    const sessionData = {
        sessionId: "session_" + session.id,
        status: 'ACTIVE',
        sessionTitle: session.sessionTitle
    }
    currentSession.value = sessionData
}

const handleDeleteSession = (sessionId) => {
    ElMessageBox.confirm('确定要删除此会话吗？此操作不可恢复', '删除确认', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(() => {
        return deleteSession(sessionId)
    }).then(() => {
        ElMessage.success('删除成功')
        // 若删除的正是当前查看的会话，则重置为新会话
        if (currentSession.value?.sessionId === `session_${sessionId}`) {
            createNewFrontendSession()
        }
        loadSessionList()
    }).catch(() => {
        // 用户取消删除，无需处理
    })
}

// ===== 会话标题编辑 =====
// editingKey 为 'current'（当前会话）或会话 id（列表项），null 表示未处于编辑态
const editingKey = ref(null)
const editingTitle = ref('')

// 进入标题编辑态
const startEditTitle = (key, title, event) => {
    event?.stopPropagation()
    editingKey.value = key
    editingTitle.value = title || ''
}

// 退出标题编辑态
const cancelEditTitle = () => {
    editingKey.value = null
    editingTitle.value = ''
}

// 列表项标题输入框按键：Enter 保存，Esc 取消
const handleListTitleKeydown = (e, session) => {
    if (e.key === 'Enter') {
        e.preventDefault()
        saveListTitle(session)
    } else if (e.key === 'Escape') {
        e.preventDefault()
        cancelEditTitle()
    }
}

// 当前会话标题输入框按键：Enter 保存，Esc 取消
const handleCurrentTitleKeydown = (e) => {
    if (e.key === 'Enter') {
        e.preventDefault()
        saveCurrentTitle()
    } else if (e.key === 'Escape') {
        e.preventDefault()
        cancelEditTitle()
    }
}

// 保存列表中某个会话的标题
const saveListTitle = async (session) => {
    // Enter 保存后输入框失焦会再次触发，这里去重
    if (editingKey.value !== session.id) return
    const title = editingTitle.value.trim()
    if (!title) {
        ElMessage.warning('会话标题不能为空')
        return
    }
    if (title === session.sessionTitle) {
        cancelEditTitle()
        return
    }
    editingKey.value = null
    try {
        await updateSessionTitle(session.id, title)
        // 实时同步到列表与当前会话
        session.sessionTitle = title
        if (currentSession.value?.sessionId === `session_${session.id}`) {
            currentSession.value.sessionTitle = title
        }
        ElMessage.success('标题已更新')
    } catch (e) {
        // 失败提示已由 request.js 统一弹出
    } finally {
        editingTitle.value = ''
    }
}

// 保存当前会话的标题：临时会话仅本地生效，已持久化的会话调用接口
const saveCurrentTitle = async () => {
    if (editingKey.value !== 'current') return
    const session = currentSession.value
    if (!session) {
        cancelEditTitle()
        return
    }
    const title = editingTitle.value.trim()
    if (!title) {
        ElMessage.warning('会话标题不能为空')
        return
    }
    if (title === session.sessionTitle) {
        cancelEditTitle()
        return
    }
    editingKey.value = null
    try {
        if (session.status === 'TEMP') {
            // 尚未创建正式会话，本地生效，发送首条消息时随会话一并提交
            session.sessionTitle = title
            return
        }
        const id = Number(String(session.sessionId).replace(/^session_/, ''))
        await updateSessionTitle(id, title)
        session.sessionTitle = title
        const item = sessionList.value.find(item => item.id === id)
        if (item) item.sessionTitle = title
        ElMessage.success('标题已更新')
    } catch (e) {
        // 失败提示已由 request.js 统一弹出
    } finally {
        editingTitle.value = ''
    }
}

onMounted(() => {
    // 初始化时获取会话列表
    loadSessionList()
    // 初始化时创建一个新会话
    createNewFrontendSession()
})

// 离开页面时清掉情绪轮询定时器，避免组件卸载后继续发请求
onUnmounted(() => {
    stopEmotionPolling()
})
</script>


<style scoped lang="scss">
.consultation-container {
    margin: 0 auto;
    width: 100%;
    max-width: 1200px;
    box-sizing: border-box;
    display: flex;
    gap: 20px;
    padding: 20px;
    .sidebar {
        width: 320px;
        .ai-assistant-info {
            margin-bottom: 20px;
            background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 252, 248, 0.95) 100%);
            border-radius: 16px;
            padding: 16px;
            box-shadow: 0 8px 32px rgba(251, 146, 60, 0.06), 0 2px 8px rgba(0, 0, 0, 0.04);
            border: 1px solid rgba(251, 146, 60, 0.08);
            backdrop-filter: blur(10px);
            transition: all 0.3s ease;
            .breathing-circle {
                width: 60px;
                height: 60px;
                background: linear-gradient(135deg, #fb923c 0%, #f59e0b 100%);
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                margin: 0 auto 12px;
                animation: breathing 4s ease-in-out infinite;
                box-shadow: 0 6px 24px rgba(251, 146, 60, 0.25);
                position: relative;
            }
            .assistant-name {
                font-size: 16px;
                font-weight: 700;
                background: linear-gradient(135deg, #fb923c, #f59e0b);
                -webkit-background-clip: text;
                -webkit-text-fill-color: transparent;
                text-align: center;
                background-clip: text;
                margin: 0 0 12px;
            }
            .online-status {
                display: flex;
                align-items: center;
                justify-content: center;
                color: #059669;
                font-size: 12px;
                font-weight: 600;
                .status-dot {
                    width: 8px;
                    height: 8px;
                    background: #059669;
                    border-radius: 50%;
                    margin-right: 8px;
                    animation: pulse 2s infinite;
                    box-shadow: 0 0 8px rgba(5, 150, 105, 0.4);
                }
            }
        }
        .session-history {
            background: white;
            border-radius: 16px;
            padding: 16px;
            box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
            margin-bottom: 20px;
            min-height: 250px;
            display: flex;
            flex-direction: column;
            .section-title {
                font-size: 16px;
                font-weight: 600;
                color: #333;
                margin: 0 0 16px;
                display: flex;
                align-items: center;
                justify-content: space-between;
                
            }
            .session-list {
                overflow-y: auto;
                max-height: 200px;
                scrollbar-width: thin;
                scrollbar-color: rgba(64, 150, 255, 0.3) transparent;
                .session-item {
                    position: relative;
                    display: flex;
                    align-items: flex-start;
                    gap: 12px;
                    padding: 12px;
                    margin-bottom: 8px;
                    border-radius: 12px;
                    cursor: pointer;
                    transition: all 0.3s ease;
                    border: 2px solid transparent;
                    &:hover {
                        background: #f8f9ff;
                        border-color: #e6f0ff;
                    }
                    &.active {
                        background: #e6f0ff;
                        border-color: #4096ff;
                    }
                    .session-info {
                        flex: 1;
                        .session-title {
                            font-weight: 500;
                            font-size: 14px;
                            color: #333;
                            margin-bottom: 4px;
                            white-space: nowrap;
                            overflow: hidden;
                            text-overflow: ellipsis;
                            .title-text {
                                cursor: pointer;
                                &:hover {
                                    color: #f59e0b;
                                }
                            }
                            .title-input {
                                width: 150px;
                            }
                            .session-meta {
                                display: flex;
                                align-items: center;
                                gap: 8px;
                                margin-bottom: 6px;
                                .session-time {
                                    font-size: 12px;
                                    color: #999;
                                }
                            }
                            .session-preview {
                                width: 200px;
                                font-size: 12px;
                                color: #666;
                                margin-bottom: 6px;
                                white-space: nowrap;
                                overflow: hidden;
                                text-overflow: ellipsis;
                            }
                            .session-stats {
                                display: flex;
                                align-items: center;
                                gap: 12px;
                                span {
                                    font-size: 12px;
                                    color: #999;
                                    display: flex;
                                    align-items: center;
                                    gap: 4px;
                                }
                            }
                        }
                        .session-actions {
                            position: absolute;
                            top: 10px;
                            right: 12px;
                        }
                    }
                }
                .no-sessions-text {
                    text-align: center;
                    font-size: 14px;
                    color: #999;
                }
            }
        }
        .emotion-garden {
            background: linear-gradient(135deg, #fef9e7 0%, #fcf4e6 50%, #f6f0e8 100%);
            border-radius: 20px;
            padding: 16px;
            margin-bottom: 20px;
            box-shadow: 0 8px 32px rgba(252, 244, 230, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.2);
            position: relative;
            overflow: hidden;
            min-height: 300px;
            
            .garden-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 20px;
                position: relative;
                z-index: 2;
                .garden-title {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    font-size: 16px;
                    font-weight: 600;
                    color: #8b4513;
                }
            }
            .emotion-info {
                margin: 0 auto;
                width: 80px;
                height: 80px;
                border-radius: 50%;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                z-index: 10;
                box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
                border: 2px solid rgba(255, 255, 255, 0.8);
                background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 50%, #fecfef 100%);
                color: #fff;
                .emotion-name {
                    font-size: 15px;
                    font-weight: 600;
                    line-height: 1;
                    margin-bottom: 2px;
                }
                .emotion-score {
                    font-size: 14px;
                    font-weight: 700;
                    opacity: 0.9;
                }
            }
            .warm-tips {
                text-align: center;
                margin-bottom: 16px;
                .emotion-status-text {
                    margin-bottom: 12px;
                    .status-label {
                        font-size: 14px;
                        color: #8b7355;
                        margin-right: 8px;
                    }
                    .status-emotion {
                        font-size: 16px;
                        font-weight: 600;
                        padding: 4px 12px;
                        border-radius: 16px;
                        display: inline-block;
                    }
                }
                .emotion-intensity {
                    margin-bottom: 16px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                    .intensity-dots {
                        display: flex;
                        gap: 4px;
                        .dot {
                            width: 8px;
                            height: 8px;
                            border-radius: 50%;
                            background: #e0e0e0;
                            transition: all 0.3s ease;
                            &.active {
                                background: linear-gradient(135deg, #ff9a9e, #fecfef);
                                transform: scale(1.2);
                                box-shadow: 0 2px 8px rgba(255, 154, 158, 0.4);
                            }
                        }
                    }
                    .intensity-text {
                        font-size: 12px;
                        color: #8b7355;
                        font-weight: 500;
                    }
                }
                .warm-suggestion {
                    background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.8));
                    border-radius: 16px;
                    padding: 12px;
                    margin-bottom: 16px;
                    display: flex;
                    align-items: flex-start;
                    gap: 10px;
                    border: 1px solid rgba(255, 255, 255, 0.6);
                    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
                    .suggestion-icon {
                        font-size: 20px;
                        flex-shrink: 0;
                        margin-top: 2px;
                    }
                    .suggestion-content {
                        text-align: left;
                        flex: 1;
                        .suggestion-title {
                            font-size: 14px;
                            font-weight: 600;
                            color: #8b7355;
                            margin-bottom: 6px;
                        }
                        .suggestion-text {
                            font-size: 13px;
                            color: #6b5b47;
                            line-height: 1.5;
                        }
                    }
                }
                .healing-actions {
                    margin-bottom: 16px;
                    .actions-title {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 8px;
                        font-size: 14px;
                        font-weight: 600;
                        color: #8b7355;
                        margin-bottom: 16px;
                    }
                    .actions-list {
                        display: flex;
                        flex-direction: column;
                        gap: 10px;
                        .action-item {
                            background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.7));
                            border-radius: 12px;
                            padding: 12px;
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            border: 1px solid rgba(255, 255, 255, 0.5);
                            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
                            text-align: left;
                            .action-icon {
                                font-size: 14px;
                                color: #ffd700;
                                flex-shrink: 0;
                            }
                            .action-text {
                                font-size: 12px;
                                color: #6b5b47;
                                line-height: 1.4;
                                flex: 1;
                            }
                        }
                    }
                }
                .risk-notice {
                    background: linear-gradient(135deg, #fff9e6, #ffeaa7);
                    border-radius: 16px;
                    padding: 16px;
                    display: flex;
                    align-items: flex-start;
                    gap: 12px;
                    border: 1px solid rgba(255, 234, 167, 0.6);
                    box-shadow: 0 6px 20px rgba(255, 234, 167, 0.3);
                    .notice-icon {
                        font-size: 20px;
                        flex-shrink: 0;
                        margin-top: 2px;
                    }
                    .notice-content {
                        flex: 1;
                        .notice-title {
                            font-size: 14px;
                            font-weight: 600;
                            color: #d4840f;
                            margin-bottom: 6px;
                        }
                        .notice-text {
                            font-size: 13px;
                            color: #b8740c;
                            line-height: 1.5;
                        }
                    }
                }
            }
        }
    }
    .chat-main {
        background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(255, 252, 250, 0.98) 100%);
        border-radius: 20px;
        box-shadow: 0 12px 40px rgba(251, 146, 60, 0.08), 0 4px 16px rgba(0, 0, 0, 0.04);
        border: 1px solid rgba(251, 146, 60, 0.1);
        backdrop-filter: blur(10px);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        flex: 1;
        .chat-header {
            background: linear-gradient(135deg, #fb923c 0%, #f59e0b 100%);
            color: white;
            padding: 20px 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: relative;
            flex-shrink: 0;
            .header-left {
                display: flex;
                align-items: center;
                .chat-avatar {
                    width: 48px;
                    height: 48px;
                    background: rgba(255, 255, 255, 0.25);
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin-right: 16px;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
                    position: relative;
                    z-index: 1;
                }
                .chat-info {
                    h2 {
                        font-size: 20px;
                        font-weight: 700;
                        margin-bottom: 4px;
                    }
                    p {
                        font-size: 14px;
                    }
                }
            }
        }
        .chat-title-bar {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 10px 24px;
            background: rgba(251, 146, 60, 0.06);
            border-bottom: 1px solid rgba(251, 146, 60, 0.1);
            flex-shrink: 0;
            .title-text {
                font-size: 15px;
                font-weight: 600;
                color: #333;
                cursor: pointer;
                padding: 2px 6px;
                border-radius: 6px;
                transition: all 0.2s ease;
                &:hover {
                    background: rgba(251, 146, 60, 0.12);
                    color: #f59e0b;
                }
            }
            .title-input {
                width: 260px;
            }
        }
        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 16px;
            background: linear-gradient(135deg, rgba(255, 255, 255, 0.02) 0%, rgba(255, 252, 248, 0.05) 100%);
            min-height: 0;
            max-height: calc(100vh - 270px);
            scrollbar-width: thin;
            scrollbar-color: rgba(251, 146, 60, 0.3) transparent;
            .message-item {
                display: flex;
                align-items: flex-start;
                gap: 12px;
                .message-avatar {
                    width: 32px;
                    height: 32px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 14px;
                    color: white;
                    flex-shrink: 0;
                }
                &.ai-message {
                    .message-avatar {
                        background: linear-gradient(135deg, #fb923c, #f59e0b);
                        box-shadow: 0 4px 12px rgba(251, 146, 60, 0.3);
                    }
                }
                &.user-message {
                    .message-avatar {
                        background: linear-gradient(135deg, #6b7280, #4b5563);
                        box-shadow: 0 4px 12px rgba(107, 114, 128, 0.3);
                    }
                }
                .message-content {
                    max-width: 70%;
                    .message-bubble {
                        background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 252, 248, 0.95) 100%);
                        border-radius: 16px;
                        padding: 12px 16px;
                        position: relative;
                        animation: fadeInUp 0.4s ease-out;
                        border: 1px solid rgba(251, 146, 60, 0.1);
                        box-shadow: 0 4px 16px rgba(251, 146, 60, 0.05);
                        // 用户消息纯文本：保留换行、长单词自动折行（替代原来的 v-html + <br>）
                        .user-message-text {
                            white-space: pre-wrap;
                            word-break: break-word;
                            margin: 0;
                        }
                        .typing-indicator {
                            display: flex;
                            gap: 4px;
                            padding: 8px 0;
                            .typing-dot {
                                width: 8px;
                                height: 8px;
                                background: #ccc;
                                border-radius: 50%;
                                animation: typing 1.5s ease-in-out infinite;
                                &:nth-child(2) {
                                    animation-delay: 0.2s;
                                }
                                &:nth-child(3) {
                                    animation-delay: 0.4s;
                                }   
                            }
                        }
                        /* 错误消息样式 */
                        .error-message {
                            background: linear-gradient(135deg, #FEF2F2 0%, #FECACA 100%);
                            border: 1px solid #F87171;
                            border-radius: 12px;
                            padding: 12px 16px;
                            color: #991B1B;
                            font-weight: 500;
                            display: flex;
                            align-items: center;
                            gap: 8px;
                        }
                    }
                    .message-time {
                        font-size: 12px;
                        color: #999;
                        margin-top: 4px;
                    }
                }
            }
        }
        .chat-input {
            border-top: 1px solid rgba(251, 146, 60, 0.1);
            padding: 20px 24px;
            display: flex;
            gap: 12px;
            align-items: flex-end;
            background: linear-gradient(135deg, rgba(255, 255, 255, 0.5) 0%, rgba(255, 252, 248, 0.7) 100%);
            backdrop-filter: blur(10px);
            flex-shrink: 0;
            .input-container {
                flex: 1;
            }
            .input-footer {
                display: flex;
                justify-content: space-between;
                align-items: center;
                font-size: 12px;
                color: #78716c;
                font-weight: 500;
            }
            .send-btn {
                height: 60px;
                width: 60px;
                border-radius: 16px;
                background: linear-gradient(135deg, #fb923c 0%, #f59e0b 100%) !important;
                border: none !important;
                box-shadow: 0 6px 20px rgba(251, 146, 60, 0.25);
                transition: all 0.3s ease;
            }

        }

    }
}

// 窄屏（平板/手机）下侧边栏与对话区改为纵向排列，避免挤压与横向溢出
@media (max-width: 992px) {
    .consultation-container {
        flex-direction: column;
        padding: 12px;
        .sidebar {
            width: 100%;
        }
    }
}
</style>