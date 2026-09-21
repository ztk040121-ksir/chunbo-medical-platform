package com.chunbo.medical.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 * 会话历史更新/AI 提炼标题等 @Async 任务若走默认 SimpleAsyncTaskExecutor 会每任务新建线程、
 * 无池化导致高并发下线程暴涨，这里统一收敛到有界线程池（队列满时由调用线程兜底执行）。
 */
@Configuration
public class AsyncConfig {

    @Bean("chatSessionExecutor")
    public Executor chatSessionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("chat-session-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
