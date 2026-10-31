package com.autofolio.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;

/**
 * Habilita @Async. O executor é o applicationTaskExecutor do Spring Boot, que usa
 * Virtual Threads por causa de spring.threads.virtual.enabled=true e aplica o TaskDecorator abaixo.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Propaga o MDC (requestId) da thread da requisição para a tarefa assíncrona e o limpa ao fim.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                var previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                } else {
                    MDC.clear();
                }
                try {
                    task.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
