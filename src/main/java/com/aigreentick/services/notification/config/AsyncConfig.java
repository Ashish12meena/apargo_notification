package com.aigreentick.services.notification.config;

import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.aigreentick.services.notification.config.properties.AsyncProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncProperties asyncProperties;

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        AsyncProperties.EmailAsyncConfig emailConfig = asyncProperties.getEmail();

        executor.setCorePoolSize(emailConfig.getCorePoolSize());
        executor.setMaxPoolSize(emailConfig.getMaxPoolSize());
        executor.setQueueCapacity(emailConfig.getQueueCapacity());
        executor.setThreadNamePrefix(emailConfig.getThreadNamePrefix());
        executor.setKeepAliveSeconds(emailConfig.getKeepAliveSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(emailConfig.getAwaitTerminationSeconds());

        executor.initialize();

        log.info("Email Task Executor initialized with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                emailConfig.getCorePoolSize(),
                emailConfig.getMaxPoolSize(),
                emailConfig.getQueueCapacity());

        return executor;
    }

    @Bean(name = "pushTaskExecutor")
    public Executor pushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        AsyncProperties.PushAsyncConfig pushConfig = asyncProperties.getPush();

        executor.setCorePoolSize(pushConfig.getCorePoolSize());
        executor.setMaxPoolSize(pushConfig.getMaxPoolSize());
        executor.setQueueCapacity(pushConfig.getQueueCapacity());
        executor.setThreadNamePrefix(pushConfig.getThreadNamePrefix());
        executor.setKeepAliveSeconds(pushConfig.getKeepAliveSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Push Task Executor initialized with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                pushConfig.getCorePoolSize(),
                pushConfig.getMaxPoolSize(),
                pushConfig.getQueueCapacity());

        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return emailTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async execution failed in method: {} with params: {}",
                    method.getName(), params, throwable);
        };
    }
}