import service from '@/utils/request'

export const register = (data) => {
    return service.post('/user/add', data)
}

export const startSession = (data) => {
    return service.post('/psychological-chat/session/start', data)
}

export const getSessionList = (params) => {
    return service.get('/psychological-chat/sessions', { params })
}

export const deleteSession = (sessionId) => {
    return service.delete(`/psychological-chat/sessions/${sessionId}`)
}

export const updateSessionTitle = (sessionId, sessionTitle) => {
    return service.put(`/psychological-chat/sessions/${sessionId}/title`, { sessionTitle })
}

export const getSessionDetail = (sessionId) => {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export const getSessionEmotion = (sessionId) => {
    return service.get(`/psychological-chat/session/${sessionId}/emotion`)
}

export const createOrUpdateEmotionDiary = (data) => {
    return service.post('/emotion-diary', data)
}

export const getTodayEmotionDiary = () => {
    return service.get('/emotion-diary/today')
}

export const getKnowledgeList = (params) => {
    return service.get('/knowledge/article/page', { params })
}

export const getKnowledgeDetail = (id) => {
    return service.get(`/knowledge/article/${id}`)
}

// 知识文章分类树（列表与详情都用它把 categoryId 转成分类名称）
export const getKnowledgeCategoryTree = () => {
    return service.get('/knowledge/category/tree')
}