package com.yuankai.aispringboot.task;

import com.yuankai.aispringboot.entity.AiAnalysisTask;
import com.yuankai.aispringboot.service.AiAnalysisTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 分析任务调度器：
 * 定时把 ai_analysis_task 表中待处理的任务投递到线程池执行。
 *
 * 采用「定时轮询 + 抢占」而不是消息队列，是为了和项目现有技术栈保持一致（只有一个应用实例），
 * 数据落在 MySQL 里可随时查看任务积压与失败原因；将来接入 MQ 时，只需把本类替换为消费者即可。
 */
@Slf4j
@Component
public class AiAnalysisTaskScheduler {

    /** 单轮最多投递的任务数：队列积压时避免一次性占满线程池，挤掉其它接口的资源 */
    private static final int MAX_TASKS_PER_ROUND = 10;

    @Autowired
    private AiAnalysisTaskService aiAnalysisTaskService;

    /**
     * fixedDelay：上一轮执行完再等 30 秒，大模型响应慢时不会出现任务重叠堆积。
     * initialDelay：错开应用启动瞬间，避免与数据库连接池初始化抢资源。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void dispatchPendingTasks() {
        try {
            // 1) 先把进程异常退出时卡在 PROCESSING 的任务捞回来，否则它们永远不会被消费
            int requeued = aiAnalysisTaskService.requeueStuckTasks();
            if (requeued > 0) {
                log.warn("回收卡死的 AI 分析任务 {} 条，已重新入队", requeued);
            }

            // 2) 拉取待处理任务并投递（executeTask 是 @Async，此处为跨 Bean 调用，异步生效）
            List<AiAnalysisTask> tasks = aiAnalysisTaskService.takePendingTasks(MAX_TASKS_PER_ROUND);
            if (tasks.isEmpty()) {
                return;
            }
            log.info("待处理 AI 分析任务 {} 条，开始投递", tasks.size());
            tasks.forEach(task -> aiAnalysisTaskService.executeTask(task.getId()));
        } catch (Exception e) {
            // 调度异常不能中断后续轮次，记录日志即可
            log.error("AI 分析任务调度失败", e);
        }
    }
}
