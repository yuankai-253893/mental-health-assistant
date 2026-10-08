/**
 * 查询条件构造辅助
 *
 * 后台各列表页都要把「表单里填了什么」拼成接口参数，共同点是：
 * 空值不能传（后端 Long 参数收到空串会绑定失败），但 0 必须传。
 */

/**
 * 判断查询条件是否「已填写」。
 *
 * 不能用真值判断：status / userType 这类枚举字段允许取 0（如「禁用」「失败」），
 * `if (val)` 会把合法的 0 当成空值丢掉，导致筛选失效且没有任何报错。
 *
 * @param {*} val
 * @returns {boolean}
 */
export const isFilled = (val) => val !== '' && val !== null && val !== undefined
