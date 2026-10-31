package com.autofolio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita @Async. O executor é o applicationTaskExecutor do Spring Boot, que usa
 * Virtual Threads por causa de spring.threads.virtual.enabled=true.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
