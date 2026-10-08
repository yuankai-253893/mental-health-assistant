import service from '@/utils/request'

// 管理后台专用接口
// 登录 / 退出登录属于管理员与用户共用的鉴权接口，见 api/auth.js
// 会话相关接口见 api/consultation.js，知识文章相关接口见 api/knowledge.js

// 数据分析总览接口
export function getAnalyticsOverview() {
    return service.get('/data-analytics/overview')
}

// 情绪日志分页查询接口
export function getEmotionalPage(params) {
    return service.get('/emotion-diary/admin/page', {params: params})
}

// 情绪日志删除接口
export function deleteEmotional(id) {
    return service.delete(`/emotion-diary/admin/${id}`)
}

// AI 分析任务分页查询接口
export function getAnalysisTaskPage(params) {
    return service.get('/ai-task/admin/page', { params })
}

// AI 分析任务重试接口（仅失败状态可重试）
export function retryAnalysisTask(taskId) {
    return service.post(`/ai-task/admin/${taskId}/retry`)
}

// 管理端为指定日记发起 AI 分析接口
export function analyzeDiaryAsAdmin(diaryId) {
    return service.post(`/ai-task/admin/diary/${diaryId}`)
}

// 上传文件接口
export function uploadFile(file, businessInfo) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('businessType', businessInfo.businessType)
    formData.append('businessId', businessInfo.businessId)
    formData.append('businessField', businessInfo.businessField)
    return service.post('/file/upload', formData,
        {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        }
    )
}

// 删除文件接口（按访问路径删除，后端会同时清理磁盘文件与 sys_file_info 记录）
export function deleteFile(fileUrl) {
    return service.delete('/file', { data: { fileUrl } })
}

// ==================== 用户管理 ====================

// 用户分页查询接口（支持 username / nickname / status / userType 筛选）
export function getUserPage(params) {
    return service.get('/user/admin/page', { params })
}

// 禁用 / 启用用户接口（status：0-禁用 1-正常）
export function updateUserStatus(id, status) {
    return service.put(`/user/admin/${id}/status`, { status })
}

// 重置用户密码接口
export function resetUserPassword(id, newPassword) {
    return service.put(`/user/admin/${id}/password`, { newPassword })
}

// ==================== 操作日志 ====================

// 操作日志分页查询接口（支持 username / operation / status / startDate / endDate 筛选）
export function getOperationLogPage(params) {
    return service.get('/operation-log/admin/page', { params })
}
