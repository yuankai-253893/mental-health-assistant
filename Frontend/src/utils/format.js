/**
 * 时间展示工具
 *
 * 后端 LocalDateTime 会被序列化成 ISO 字符串（如 2025-09-14T14:11:53），
 * 直接渲染会出现多余的 "T"。统一在这里裁剪，避免各页面各写一份。
 */

/**
 * 格式化为 YYYY-MM-DD HH:mm（分钟精度）
 * @param {string|Date|number} val 时间值，为空时返回 '-'
 * @returns {string}
 */
export const formatDateTime = (val) => {
    if (!val) return '-'
    return String(val).replace('T', ' ').slice(0, 16)
}
