package com.yuankai.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.response.AiAnalysisTaskResponseDTO;
import com.yuankai.aispringboot.entity.AiAnalysisTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiAnalysisTaskMapper extends BaseMapper<AiAnalysisTask> {

    /**
     * 取一批待处理任务：优先级高的先执行，同优先级按入队顺序（FIFO），避免低优先级任务饿死。
     * 取出的量由调用方控制，防止一次性把队列全捞进内存。
     */
    @Select("""
            SELECT * FROM ai_analysis_task
            WHERE status = 'PENDING'
            ORDER BY priority DESC, id ASC
            LIMIT #{limit}
            """)
    List<AiAnalysisTask> selectPendingTasks(@Param("limit") int limit);

    /**
     * 抢占任务：把 PENDING 置为 PROCESSING。
     * 带 status = 'PENDING' 前置条件，多消费者并发时只有一方能更新成功（影响行数=1），
     * 以此代替分布式锁，避免同一条任务被重复执行。
     */
    @Update("""
            UPDATE ai_analysis_task
            SET status = 'PROCESSING', started_at = NOW(), updated_at = NOW()
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markProcessing(@Param("id") Long id);

    /**
     * 回收卡死的 PROCESSING 任务。
     * 服务在任务执行途中被 kill，任务会永远停在 PROCESSING，永远不会再被消费；
     * 这里把超时未完成的任务退回 PENDING 重新排队。
     */
    @Update("""
            UPDATE ai_analysis_task
            SET status = 'PENDING',
                error_message = CONCAT('任务执行超时（超过 ', #{timeoutMinutes}, ' 分钟），已重新入队'),
                updated_at = NOW()
            WHERE status = 'PROCESSING'
              AND started_at < DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE)
            """)
    int requeueStuckTasks(@Param("timeoutMinutes") int timeoutMinutes);

    /**
     * 管理端任务分页。status / userId / diaryId 均为可选筛选条件，
     * 条件为空时不参与 WHERE，避免拼出 `status = null` 查不到数据。
     */
    @Select("""
            <script>
            SELECT t.id              AS id,
                   t.diary_id        AS diaryId,
                   t.user_id         AS userId,
                   u.username        AS username,
                   t.status          AS status,
                   t.task_type       AS taskType,
                   t.priority        AS priority,
                   t.retry_count     AS retryCount,
                   t.max_retry_count AS maxRetryCount,
                   t.error_message   AS errorMessage,
                   t.started_at      AS startedAt,
                   t.completed_at    AS completedAt,
                   t.created_at      AS createdAt
            FROM ai_analysis_task t
                     LEFT JOIN `user` u ON u.id = t.user_id
            <where>
                <if test="status != null and status != ''">AND t.status = #{status}</if>
                <if test="userId != null">AND t.user_id = #{userId}</if>
                <if test="diaryId != null">AND t.diary_id = #{diaryId}</if>
            </where>
            ORDER BY t.id DESC
            </script>
            """)
    Page<AiAnalysisTaskResponseDTO> selectTaskPage(Page<AiAnalysisTaskResponseDTO> page,
                                                   @Param("status") String status,
                                                   @Param("userId") Long userId,
                                                   @Param("diaryId") Long diaryId);
}
