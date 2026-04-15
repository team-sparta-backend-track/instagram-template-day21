package com.example.instagramclone.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 알림 처리 전용 스레드 풀 설정.
 *
 * 카페 비유:
 * - corePoolSize(5)  = 기본 바리스타 5명 (평소에 항상 대기)
 * - maxPoolSize(10)  = 최대 바리스타 10명 (바쁠 때 임시 투입)
 * - queueCapacity(50) = 대기 좌석 50석 (바리스타가 다 바쁘면 여기서 대기)
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notif-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
