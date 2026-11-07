package com.reelay.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String PIPELINE_EXECUTOR = "pipelineExecutor";

    // VideoPipeline 전용 풀. 1차는 로컬 단일 인스턴스라 동시에 2개 job 까지만 돌린다
    // 렌더(FFmpeg) 동시 실행 상한은 VideoPipeline 의 세마포어가 따로 막는다
    @Bean(name = PIPELINE_EXECUTOR)
    public ThreadPoolTaskExecutor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("pipeline-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
