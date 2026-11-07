package com.reelay.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 활성화 (FailedTmpCleaner)
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
