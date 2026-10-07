import service from '@/utils/request'

// 管理后台接口
// 登录 / 退出登录属于管理员与用户共用的鉴权接口，见 api/auth.js

// 数据分析总览接口
export function getAnalyticsOverview() {
    return service.get('/data-analytics/overview')
}


// 会话详情接口
export function getSessionDetail(id) {
    return service.get(`/psychological-chat/sessions/${id}/messages`)
}

// 咨询记录分页查询接口
export function getConsultationPage(params) {
    return service.get('/psychological-chat/sessions', {
        params: params
    })
}


// 分类树接口
export function categoryTree() {
    return service.get('/knowledge/category/tree')
}

// 文章分页查询接口
export function articlePage(params) {
    return service.get('/knowledge/admin/article/page', {params: params})
}

// 文章详情接口
export function getArticleDetail(id) {
    return service.get(`/knowledge/article/${id}`)
}

// 文章发布/下线接口
export function changeArticleStatus(id, data) {
    return service.put(`/knowledge/admin/article/${id}/status`, data)
}

// 文章创建接口
export function createArticle(data) {
    return service.post('/knowledge/article', data)
}

// 文章更新接口
export function updateArticle(id, data) {
    return service.put(`/knowledge/admin/article/${id}`, data)
}

// 文章删除接口
export function deleteArticle(id) {
    return service.delete(`/knowledge/admin/article/${id}/delete`)
}

// 情绪日志分页查询接口
export function getEmotionalPage(params) {
    return service.get('/emotion-diary/admin/page', {params: params})
}

// 情绪日志删除接口
export function deleteEmotional(id) {
    return service.delete(`/emotion-diary/admin/${id}`)
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

