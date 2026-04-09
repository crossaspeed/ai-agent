package com.it.ai.aiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "chatStreamExecutor")
    public AsyncTaskExecutor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-stream-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}