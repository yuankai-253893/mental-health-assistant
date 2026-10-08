package com.yuankai.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据分析仪表盘响应结构。
 * 与前端 展示区域一一对应：概览卡片、情绪趋势、咨询统计、用户活跃度。
 *
 * 注意：本类会被整块序列化进 Redis 缓存并反序列化回来，
 * 因此本类与所有嵌套类都必须保留 @NoArgsConstructor（Jackson 反序列化需要无参构造），
 * @AllArgsConstructor 供 @Builder 生成构建器使用，两者都不能删。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAnalyticsResponseDTO {

    // 系统概览（顶部四张卡片）
    private SystemOverview systemOverview;

    // 近 7 日情绪趋势
    private List<EmotionTrend> emotionTrend;

    // 咨询会话统计
    private ConsultationStats consultationStats;

    // 近 7 日用户活跃度
    private List<UserActivity> userActivity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemOverview {
        // 总用户数
        private Long userTotal;

        // 今日活跃用户数
        private Long todayActive;

        // 情绪日记总数
        private Long emotionDiaryTotal;

        // 今日新增日记数
        private Long todayNewDiaries;

        // 咨询会话总数
        private Long totalSessions;

        // 今日新增会话数
        private Long todayNewSessions;

        // 日记情绪均值
        private Double avgMoodScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionTrend {
        // 日记日期
        private String date;

        // 平均情绪分数
        private Double avgMoodScore;

        // 日记数量
        private Long recordCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsultationStats {
        // 会话总数
        private Long totalSessions;

        // 消息总数
        private Long messageTotal;

        // 近 7 日会话趋势
        private List<ConsultationDaily> dailyTrend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsultationDaily {
        // 日期
        private String date;

        // 当日会话数
        private Long sessionCount;

        // 当日参与用户数
        private Long userCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivity {
        // 日期
        private String date;

        // 当日活跃用户数（写过日记或开过会话）
        private Long activeUsers;

        // 当日新增注册用户数
        private Long newUsers;

        // 当日写日记的用户数
        private Long diaryUsers;

        // 当日发起咨询的用户数
        private Long consultationUsers;
    }
}
