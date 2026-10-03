package com.broiler_monitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    /**
     * Часы в поясе JVM. Пояс задаётся при старте (APP_TIMEZONE, по умолчанию Europe/Samara),
     * поэтому LocalDateTime.now(clock) и LocalDateTime.now() в сущностях всегда совпадают.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
