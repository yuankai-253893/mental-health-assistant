import service from '@/utils/request'

// 知识文章接口
// 用户端（阅读）与管理端（增删改）共用同一批接口，只是路径前缀不同：
// 不带 admin 的为公开接口，带 admin 的由后端 @PreAuthorize 限制为管理员。
// 合并到一个文件是为了避免同一 URL 在 admin.js / user.js 各维护一份。

// 文章分类树（公开；列表页与详情页都用它把 categoryId 转成分类名）
export const getCategoryTree = () => {
    return service.get('/knowledge/category/tree')
}

// 用户端文章列表（仅已发布，支持 categoryId / sortField / sortDirection）
export const getArticlePage = (params) => {
    return service.get('/knowledge/article/page', { params })
}

// 文章详情（公开；未登录只能看已发布文章，管理员可看草稿/已下线）
export const getArticleDetail = (id) => {
    return service.get(`/knowledge/article/${id}`)
}

// 管理端文章列表（含草稿与已下线，支持 categoryId / title / status / authorName）
export const getAdminArticlePage = (params) => {
    return service.get('/knowledge/admin/article/page', { params })
}

// 创建文章
export const createArticle = (data) => {
    return service.post('/knowledge/article', data)
}

// 更新文章
export const updateArticle = (id, data) => {
    return service.put(`/knowledge/admin/article/${id}`, data)
}

// 发布 / 下线文章（status：0-草稿 1-已发布 2-已下线）
export const changeArticleStatus = (id, data) => {
    return service.put(`/knowledge/admin/article/${id}/status`, data)
}

// 删除文章
export const deleteArticle = (id) => {
    return service.delete(`/knowledge/admin/article/${id}/delete`)
}
