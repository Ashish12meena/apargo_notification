package com.aigreentick.services.notification.client.audit.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for Audit Feign Client
 */
@Configuration
public class AuditClientConfig {
    
    /**
     * Feign logger level
     * NONE for production, FULL for debugging
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;  // Log only request method and URL
    }
    
    /**
     * Request timeout configuration
     * Short timeout - we don't want to wait long for audit
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            2000,  // Connect timeout: 2 seconds
            TimeUnit.MILLISECONDS,
            5000,  // Read timeout: 5 seconds
            TimeUnit.MILLISECONDS,
            true   // Follow redirects
        );
    }
    
    /**
     * Retry configuration
     * Retry once if audit service is temporarily unavailable
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            1000,  // Initial retry interval: 1 second
            2000,  // Max retry interval: 2 seconds
            2      // Max attempts: 2 (1 original + 1 retry)
        );
    }
}