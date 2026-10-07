import service from '@/utils/request'

// 登录接口（管理员与普通用户共用同一入口，由后端按 userType 区分）
export function login(data) {
    return service.post('/user/login', data)
}

// 退出登录接口
export function logout() {
    return service.post('/user/logout')
}