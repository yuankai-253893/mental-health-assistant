import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 业务状态码
const CODE_SUCCESS = '200'          // 业务成功
const CODE_UNAUTHORIZED = '-1'      // 登录过期/未登录（由 HTTP 200 承载）

// 统一的请求错误对象：携带业务码、HTTP 状态码与接口地址，便于调用方分类处理
export class ApiError extends Error {
    constructor(message, options = {}) {
        super(message)
        this.name = 'ApiError'
        this.code = options.code             // 业务状态码，如 '500'、'-1'
        this.httpStatus = options.httpStatus // HTTP 状态码，如 401、500
        this.url = options.url               // 出错接口地址
        this.response = options.response     // 原始响应，便于排查
    }
}

// HTTP 状态码兜底文案
const HTTP_STATUS_MESSAGE = {
    400: '请求参数有误',
    401: '登录已过期，请重新登录',
    403: '没有权限执行该操作',
    404: '请求的资源不存在',
    405: '请求方法不被允许',
    408: '请求超时，请稍后重试',
    500: '服务器内部错误，请稍后重试',
    502: '网关错误',
    503: '服务暂不可用，请稍后重试',
    504: '网关超时'
}

// 创建 axios 实例
const service = axios.create({
    baseURL: '/api',    // 请求前缀
    timeout: 5000       // 超时时间 5 秒
})

// 请求拦截器
service.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['token'] = token
        }
        return config
    },
    error => Promise.reject(error)
)

// 是否为登录接口：登录失败应展示后端提示，而不是当作「登录过期」跳转
const isLoginRequest = (url = '') => url.includes('/user/login')

// 登录态失效统一处理：提示 + 清理本地凭证 + 跳转登录页
const handleUnauthorized = (message) => {
    ElMessage.error(message || '登录已过期，请重新登录')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    router.push({ name: 'login' })
}

// 响应拦截器
service.interceptors.response.use(
    response => {
        const { data, config } = response
        const url = config?.url || ''

        // 非标准 Result 结构（如文件流等）：原样返回
        if (!data || typeof data.code === 'undefined') {
            return data
        }

        const code = String(data.code)

        // 业务成功：只返回 data.data，保持既有调用约定
        if (code === CODE_SUCCESS) {
            return data.data
        }

        // 业务失败：统一提示，并抛出规范化错误，避免把错误响应当作正常数据返回
        const message = data.msg || '操作失败，请稍后重试'
        if (code === CODE_UNAUTHORIZED) {
            if (isLoginRequest(url)) {
                ElMessage.error(message)
            } else {
                handleUnauthorized(message)
            }
        } else {
            ElMessage.error(message)
        }

        return Promise.reject(new ApiError(message, {
            code,
            httpStatus: response.status,
            url,
            response
        }))
    },
    error => {
        const { response, config, code: errCode, message: errMessage } = error
        const url = config?.url || ''

        // 1) 无响应：网络中断或超时
        if (!response) {
            const isTimeout = errCode === 'ECONNABORTED' || /timeout/i.test(errMessage || '')
            const message = isTimeout ? '请求超时，请稍后重试' : '网络连接失败，请检查网络'
            ElMessage.error(message)
            return Promise.reject(new ApiError(message, { code: errCode, url }))
        }

        const status = response.status

        // 2) 登录态失效（HTTP 401）
        if (status === 401) {
            const message = response.data?.msg || '登录已过期，请重新登录'
            if (isLoginRequest(url)) {
                ElMessage.error(message)
            } else {
                handleUnauthorized(message)
            }
            return Promise.reject(new ApiError(message, { httpStatus: status, url, response }))
        }

        // 3) 其余 HTTP 错误按状态码分类提示
        const message = response.data?.msg
            || HTTP_STATUS_MESSAGE[status]
            || `请求失败（${status}）`
        ElMessage.error(message)
        return Promise.reject(new ApiError(message, { httpStatus: status, url, response }))
    }
)

// 导出 axios 实例
export default service
