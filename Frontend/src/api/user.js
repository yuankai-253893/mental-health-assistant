import service from '@/utils/request'

// 用户端接口
// 会话相关接口见 api/consultation.js，知识文章相关接口见 api/knowledge.js
// （这两组接口用户端与管理端共用，不在这里重复维护）

export const register = (data) => {
    return service.post('/user/add', data)
}

// 获取当前登录用户信息。
// 用途：token 仍在有效期内、但本地 userInfo 丢失（清缓存 / 换浏览器）时，
// 由路由守卫调用它把用户信息补回来，避免有效登录态被误判成未登录。
export const getCurrentUser = () => {
    return service.get('/user/current')
}

export const createOrUpdateEmotionDiary = (data) => {
    return service.post('/emotion-diary', data)
}

export const getTodayEmotionDiary = () => {
    return service.get('/emotion-diary/today')
}

// ==================== 文章收藏 ====================

// 收藏文章（幂等，重复收藏不报错）
export const addFavorite = (articleId) => {
    return service.post(`/favorite/${articleId}`)
}

// 取消收藏（幂等）
export const removeFavorite = (articleId) => {
    return service.delete(`/favorite/${articleId}`)
}

// 查询当前用户是否已收藏该文章
export const checkFavorite = (articleId) => {
    return service.get(`/favorite/check/${articleId}`)
}

// 我的收藏分页
export const getFavoritePage = (params) => {
    return service.get('/favorite/page', { params })
}

// ==================== AI 情绪分析任务 ====================

// 触发某条日记的 AI 情绪分析（异步，返回任务信息）
export const triggerDiaryAnalysis = (diaryId) => {
    return service.post(`/ai-task/diary/${diaryId}`)
}

// 查询某条日记最新的分析任务状态（用于轮询分析进度）
export const getDiaryAnalysisTask = (diaryId) => {
    return service.get(`/ai-task/diary/${diaryId}`)
}
