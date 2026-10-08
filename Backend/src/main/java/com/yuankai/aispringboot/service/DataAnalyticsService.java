package com.yuankai.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuankai.aispringboot.DTO.response.DataAnalyticsResponseDTO;
import com.yuankai.aispringboot.DTO.response.DataAnalyticsResponseDTO.ConsultationDaily;
import com.yuankai.aispringboot.DTO.response.DataAnalyticsResponseDTO.ConsultationStats;
import com.yuankai.aispringboot.DTO.response.DataAnalyticsResponseDTO.EmotionTrend;
import com.yuankai.aispringboot.DTO.response.DataAnalyticsResponseDTO.SystemOverview;
import com.yuankai.aispringboot.DTO.response.DataAnalyticsResponseDTO.UserActivity;
import com.yuankai.aispringboot.entity.EmotionDiary;
import com.yuankai.aispringboot.mapper.ConsultationMessageMapper;
import com.yuankai.aispringboot.mapper.ConsultationSessionMapper;
import com.yuankai.aispringboot.mapper.EmotionDiaryMapper;
import com.yuankai.aispringboot.mapper.UserMapper;
import com.yuankai.aispringboot.consts.RedisKeyConsts;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DataAnalyticsService {

    /**
     * 仪表盘聚合缓存 TTL（秒）。
     * 一屏数据由 12 条 SQL 聚合 + 1 次 Redis 计数拼出、管理端不要求强实时，
     * 60 秒内只查库一次，把「一屏 = 12 次聚合」压成「60 秒内 N 次请求 = 1 次 GET」。
     */
    private static final long ANALYTICS_CACHE_TTL_SECONDS = 60;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmotionDiaryMapper emotionDiaryMapper;

    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;

    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    @Autowired
    private ActiveUserRecordService activeUserRecordService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 复用 Spring 容器中已配置好的 ObjectMapper（Spring Boot 4 使用 Jackson 3，类型为 tools.jackson.databind.ObjectMapper）
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 数据分析仪表盘（Cache Aside：读缓存 → 未命中查库 → 回填缓存）。
     * Redis 全程 fail-open：读失败 / 反序列化失败 / 写失败都只记日志，不影响数据返回。
     */
    public DataAnalyticsResponseDTO getDataAnalytics() {
        // 1. 缓存优先：命中直接返回，避免一屏触发 12 条聚合查询
        try {
            String cached = redisTemplate.opsForValue().get(RedisKeyConsts.DATA_ANALYTICS_OVERVIEW_KEY);
            if (StrUtil.isNotBlank(cached)) {
                log.info("数据分析仪表盘缓存命中");
                return objectMapper.readValue(cached, DataAnalyticsResponseDTO.class);
            }
        } catch (Exception e) {
            // Redis 不可用 / 缓存 JSON 与当前 DTO 结构不兼容（如字段增删）都回退查库，不阻塞主流程
            log.warn("数据分析仪表盘缓存读取失败，回退数据库查询", e);
        }

        // 2. 未命中 → 查库聚合
        DataAnalyticsResponseDTO result = buildDataAnalytics();

        // 3. 回填缓存（TTL 兜底；聚合数据来自全站实时写入，没有合适的主动失效时机，靠 TTL 到期自然刷新）
        try {
            redisTemplate.opsForValue().set(RedisKeyConsts.DATA_ANALYTICS_OVERVIEW_KEY,
                    objectMapper.writeValueAsString(result), ANALYTICS_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("数据分析仪表盘缓存写入失败", e);
        }
        return result;
    }

    // 查库聚合：4 张表 COUNT + 1 条情绪均值 + 2 条「今日新增」+ 5 条「近 7 日」分组，共 12 条 SQL
    private DataAnalyticsResponseDTO buildDataAnalytics() {
        // 各表总数
        Long userTotal = userMapper.selectCount(null);
        Long emotionDiaryTotal = emotionDiaryMapper.selectCount(null);
        Long totalSessions = consultationSessionMapper.selectCount(null);
        Long messageTotal = consultationMessageMapper.selectCount(null);

        // 日记情绪均值
        QueryWrapper<EmotionDiary> avgWrapper = new QueryWrapper<>();
        avgWrapper.select("AVG(mood_score) AS averageMoodScore");
        Map<String, Object> avgResult = emotionDiaryMapper.selectMaps(avgWrapper).get(0);
        Double avgMoodScore = toDouble(avgResult.get("averageMoodScore"));

        // 今日活跃用户（Redis HyperLogLog 去重计数，Redis 故障自动回退 SQL）
        Long todayActive = activeUserRecordService.countToday();

        // 今日新增
        Long todayNewDiaries = emotionDiaryMapper.selectTodayNewDiaries();
        Long todayNewSessions = consultationSessionMapper.selectTodayNewSessions();

        // 顶部概览
        SystemOverview systemOverview = SystemOverview.builder()
                .userTotal(userTotal)
                .todayActive(todayActive)
                .emotionDiaryTotal(emotionDiaryTotal)
                .todayNewDiaries(todayNewDiaries)
                .totalSessions(totalSessions)
                .todayNewSessions(todayNewSessions)
                .avgMoodScore(avgMoodScore)
                .build();

        // 近 7 日情绪趋势
        List<EmotionTrend> emotionTrend = emotionDiaryMapper.selectLast7DaysMoodStats().stream()
                .map(m -> EmotionTrend.builder()
                        .date(m.get("diaryDate") == null ? null : m.get("diaryDate").toString())
                        .avgMoodScore(toDouble(m.get("avgMoodScore")))
                        .recordCount(toLong(m.get("diaryCount")))
                        .build())
                .toList();

        // 咨询会话统计：近 7 日按天补零，避免图表出现断点
        Map<String, Map<String, Object>> sessionStats = indexByDate(consultationSessionMapper.selectLast7DaysSessionStats());
        List<ConsultationDaily> dailyTrend = last7Days().stream()
                .map(day -> ConsultationDaily.builder()
                        .date(day)
                        .sessionCount(valueOf(sessionStats, day, "sessionCount"))
                        .userCount(valueOf(sessionStats, day, "userCount"))
                        .build())
                .toList();
        ConsultationStats consultationStats = ConsultationStats.builder()
                .totalSessions(totalSessions)
                .messageTotal(messageTotal)
                .dailyTrend(dailyTrend)
                .build();

        // 近 7 日用户活跃度：活跃用户来自日记与咨询的去重并集
        Map<String, Map<String, Object>> activeMap = indexByDate(emotionDiaryMapper.selectLast7DaysActiveUsers());
        Map<String, Map<String, Object>> newUserMap = indexByDate(userMapper.selectLast7DaysNewUsers());
        Map<String, Map<String, Object>> diaryUserMap = indexByDate(emotionDiaryMapper.selectLast7DaysDiaryUsers());
        List<UserActivity> userActivity = last7Days().stream()
                .map(day -> UserActivity.builder()
                        .date(day)
                        .activeUsers(valueOf(activeMap, day, "cnt"))
                        .newUsers(valueOf(newUserMap, day, "cnt"))
                        .diaryUsers(valueOf(diaryUserMap, day, "cnt"))
                        .consultationUsers(valueOf(sessionStats, day, "userCount"))
                        .build())
                .toList();

        return DataAnalyticsResponseDTO.builder()
                .systemOverview(systemOverview)
                .emotionTrend(emotionTrend)
                .consultationStats(consultationStats)
                .userActivity(userActivity)
                .build();
    }

    // 近 7 日日期（含今天），用于把按天的聚合结果补零成连续序列
    private List<String> last7Days() {
        List<String> days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            days.add(today.minusDays(i).toString());
        }
        return days;
    }

    // 把 [date, 聚合行] 的列表转成 date -> 行 的索引，便于补齐缺失日期
    private Map<String, Map<String, Object>> indexByDate(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("date");
            if (date != null) {
                index.put(date.toString(), row);
            }
        }
        return index;
    }

    // 取某天某列的值，缺失日期按 0 处理
    private Long valueOf(Map<String, Map<String, Object>> index, String date, String column) {
        Map<String, Object> row = index.get(date);
        Long value = row == null ? null : toLong(row.get(column));
        return value == null ? 0L : value;
    }

    // MySQL 的 AVG 返回 BigDecimal、COUNT 返回 Long，统一用 Number 父类安全转 Double
    private Double toDouble(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }

    private Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }
}
