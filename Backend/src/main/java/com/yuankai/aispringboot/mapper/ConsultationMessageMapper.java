package com.yuankai.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuankai.aispringboot.DTO.SessionMessageStatDTO;
import com.yuankai.aispringboot.entity.ConsultationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessage> {

    // 按会话批量统计消息数与最后一条消息ID
    // 会话列表页每条会话都要展示「消息数 + 最后一条消息预览」，
    // 逐条查会产生 2N 次查询，这里用 GROUP BY 压成 1 次
    @Select("""
            <script>
            SELECT session_id AS sessionId,
                   COUNT(*)   AS messageCount,
                   MAX(id)    AS lastMessageId
            FROM consultation_message
            WHERE session_id IN
            <foreach collection="sessionIds" item="sessionId" open="(" separator="," close=")">
                #{sessionId}
            </foreach>
            GROUP BY session_id
            </script>
            """)
    List<SessionMessageStatDTO> selectMessageStats(@Param("sessionIds") Collection<Long> sessionIds);
}
