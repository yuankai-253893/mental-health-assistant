// 文件访问基础地址：改为空字符串走相对路径（/files/bussiness/...），
// 本地开发由 vite.config.js 的 /files 代理转发到后端，部署时由网关/nginx 转发，
// 不再硬编码 localhost，避免换环境/部署后图片地址失效
export const fileBaseUrl = ''
