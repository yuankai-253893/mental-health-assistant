package com.yuankai.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuankai.aispringboot.entity.ConsultationSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConsultationSessionMapper extends BaseMapper<ConsultationSession> {

    @Select("SELECT COUNT(*) FROM consultation_session WHERE DATE(started_at) = CURDATE()")
    Long selectTodayNewSessions();

    @Select("""
        SELECT DATE(started_at) AS date,
               COUNT(*) AS sessionCount,
               COUNT(DISTINCT user_id) AS userCount
        FROM consultation_session
        WHERE started_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
        GROUP BY DATE(started_at)
        """)
    List<Map<String, Object>> selectLast7DaysSessionStats();
}
