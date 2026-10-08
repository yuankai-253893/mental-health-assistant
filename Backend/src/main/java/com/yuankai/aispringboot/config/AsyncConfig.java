package com.yuankai.aispringboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 * AI 分析是「IO 密集 + 慢响应」任务（一次大模型调用可能数秒），
 * 必须与 Web 请求线程隔离，否则并发分析会占满 Tomcat 线程导致接口无响应。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数刻意压小：上游大模型有速率限制，并发过高只会换来大量失败重试
        executor.setCorePoolSize(2);                     // 核心线程数
        executor.setMaxPoolSize(4);                      // 最大线程数
        executor.setQueueCapacity(100);                  // 队列容量
        executor.setKeepAliveSeconds(60);                // 线程空闲时间
        executor.setThreadNamePrefix("ai-task-");        // 线程名称前缀
        // 队列满时由调用线程执行，宁可拖慢调度轮次，也不丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();                           // 初始化
        return executor;
    }

    /**
     * 咨询会话情绪分析专用线程池，刻意与日记分析队列隔离。
     *
     * 为什么不复用 aiTaskExecutor：调度器每轮会批量派发日记分析任务，
     * 线程被占满时后到的会话情绪分析只能排队，前端轮询会长时间拉不到结果。
     *
     * 拒绝策略用「丢弃 + 告警」而不是 CallerRunsPolicy：本方法由 SSE 流式收尾线程触发，
     * CallerRunsPolicy 会让那次大模型调用直接压在收尾线程上，把 done 事件推迟数秒，
     * 用户会看到「AI 正在输入」迟迟不消失。情绪分析是附加能力，宁可丢一次也不阻塞对话。
     */
    @Bean("consultationEmotionExecutor")
    public Executor consultationEmotionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("chat-emotion-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("会话情绪分析任务队列已满，本次分析被丢弃：active={}, pool={}",
                        pool.getActiveCount(), pool.getPoolSize()));
        executor.initialize();
        return executor;
    }
}
