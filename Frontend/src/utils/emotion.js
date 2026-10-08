/**
 * 情绪展示统一工具
 *
 * 后台「情绪日志」「咨询记录」、用户端「情绪花园」等多处都要把 AI 返回的
 * 情绪词 / 积极度分数 / 风险等级转成标签颜色与文案。
 *
 * 为什么必须收敛到一处：这些映射原先在 Emotional.vue 与 Consultations.vue
 * 各存了一份，且已经漂移 —— 同一个「平静」在一页是灰色(info)、另一页是绿色(success)；
 * 「加载更早」改造时又复制了一次。映射一旦分叉，同一个情绪在不同页面显示不同颜色
 * 是必然结果，且很难被发现。因此统一放在这里，各页面只负责引用。
 */

/**
 * 情绪词 → el-tag 的 type。
 * 取值是各页面原映射的并集；「平静」统一为 success（与 AI 分析口径一致）。
 */
const EMOTION_TAG_MAP = {
    快乐: 'success',
    开心: 'success',
    平静: 'success',
    满足: 'success',
    兴奋: 'warning',
    焦虑: 'warning',
    压力: 'warning',
    愤怒: 'danger',
    恐惧: 'danger',
    悲伤: 'info',
    沮丧: 'info',
    低落: 'info'
}

/**
 * 情绪词 → 标签类型，未收录的词兜底为 info
 * @param {string} emotion 情绪词，如「焦虑」
 * @returns {'success'|'warning'|'danger'|'info'}
 */
export const getEmotionTagType = (emotion) => EMOTION_TAG_MAP[emotion] || 'info'

/**
 * 情绪积极度 → 进度条颜色
 * emotionScore 是 0-100 的「积极程度」（不是消极程度），越高越积极，故高分用绿色。
 * @param {number} score
 * @returns {string} 十六进制色值
 */
export const getEmotionScoreColor = (score) => {
    if (score >= 80) return '#67c23a'
    if (score >= 60) return '#95d475'
    if (score >= 40) return '#e6a23c'
    return '#f56c6c'
}

/** 风险等级文案：0-正常 1-需关注 2-需心理疏导 3-危机 */
export const RISK_LEVEL_TEXT = ['情绪稳定', '需要关注', '需要心理疏导', '危机预警']

/**
 * 风险等级 → 文案
 * @param {number} level 0-3
 */
export const riskLevelText = (level) => {
    if (level === null || level === undefined || level === '') return '-'
    return RISK_LEVEL_TEXT[level] || '未知'
}

/**
 * 风险等级 → 标签类型
 * @param {number} level 0-3
 */
export const getRiskTagType = (level) => {
    if (level >= 3) return 'danger'
    if (level >= 2) return 'warning'
    if (level >= 1) return 'info'
    return 'success'
}

/**
 * 风险等级的「紧凑文案」，用户端情绪花园的强度指示条用。
 * 和上面的 riskLevelText 是同一语义的两种详略度 —— 放在一起是为了让差异可见，
 * 而不是散在两个文件里各写一份 switch。
 */
export const RISK_LEVEL_SHORT_TEXT = ['正常', '关注', '预警', '危机']

/**
 * 风险等级 → 紧凑文案
 * @param {number} level 0-3
 */
export const riskLevelShortText = (level) => {
    if (level === null || level === undefined || level === '') return '正常'
    return RISK_LEVEL_SHORT_TEXT[level] || '正常'
}

/**
 * 解析情绪分析结果。
 *
 * 同一个字段会有两种形态：会话列表接口返回的是 JSON 字符串
 * （库表 last_emotion_analysis 是 text），情绪分析接口返回的已经是对象。
 * 统一在这里吃掉差异，解析失败或为空时返回 null，
 * 让调用方走「未分析」分支，而不是渲染出 undefined 的空壳。
 *
 * @param {string|object|null} raw
 * @returns {object|null}
 */
export const parseEmotionAnalysis = (raw) => {
    if (!raw) return null
    if (typeof raw === 'object') {
        return Object.keys(raw).length ? raw : null
    }
    try {
        const data = JSON.parse(raw)
        return data && Object.keys(data).length ? data : null
    } catch (e) {
        return null
    }
}
