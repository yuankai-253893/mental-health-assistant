package com.yuankai.aispringboot.DTO.response;

import lombok.Data;

import java.util.List;

/**
 * AI 情绪分析结果，也是大模型结构化输出的目标类型。
 * 字段与 emotion_diary.ai_emotion_analysis 里既有的 JSON 结构保持一致，
 */
@Data
public class EmotionAnalysisResultDTO {
    // 主要情绪，如：焦虑 / 开心 / 平静
    private String primaryEmotion;

    // 情绪分值 0-100（越高越积极）
    private Integer emotionScore;

    // 是否为负面情绪
    private Boolean isNegative;

    // 风险等级 0-正常 1-关注 2-预警 3-危机
    private Integer riskLevel;

    // 关键词
    private List<String> keywords;

    // 给用户的一句话建议
    private String suggestion;

    // 展示用 emoji
    private String icon;

    // 展示用短标签
    private String label;

    // 风险等级的中文描述
    private String riskDescription;

    // 可执行的改善建议
    private List<String> improvementSuggestions;

    // 分析完成时间戳（写入前由服务端补全，不依赖模型生成）
    private Long timestamp;
}
