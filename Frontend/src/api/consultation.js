import service from '@/utils/request'

// AI 心理咨询会话接口
// 用户端与管理端共用同一批接口：后端按当前登录角色决定数据范围
// （管理员可查看/修改全部会话，普通用户仅能操作自己的会话）。

// 开始一个咨询会话
export const startSession = (data) => {
    return service.post('/psychological-chat/sessions', data)
}

// 分页查询会话（管理员可通过 userId 把范围缩小到指定用户）
export const getSessionPage = (params) => {
    return service.get('/psychological-chat/sessions', { params })
}

// 删除会话（该会话下的消息会级联删除）
export const deleteSession = (sessionId) => {
    return service.delete(`/psychological-chat/sessions/${sessionId}`)
}

// 修改会话标题
export const updateSessionTitle = (sessionId, sessionTitle) => {
    return service.put(`/psychological-chat/sessions/${sessionId}/title`, { sessionTitle })
}

// 查询会话消息（后端默认只返回最近 50 条、最多 200 条，按时间升序）
export const getSessionMessages = (sessionId, params) => {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`, { params })
}

// 查询会话的情绪分析结果（异步写入，可能返回空）
export const getSessionEmotion = (sessionId) => {
    return service.get(`/psychological-chat/sessions/${sessionId}/emotion`)
}
