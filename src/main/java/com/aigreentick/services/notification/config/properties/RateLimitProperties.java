package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "ratelimit")
@Data
@Validated
public class RateLimitProperties {
    
    private boolean enabled = true;
    
    private GlobalLimit global = new GlobalLimit();
    private ServiceLimit perService = new ServiceLimit();
    
    @Data
    public static class GlobalLimit {
        @Min(1)
        private int requestsPerMinute = 1000;  // System-wide capacity
        
        @Min(1)
        private int burstCapacity = 1500;
    }
    
    @Data
    public static class ServiceLimit {
        private boolean enabled = true;
        
        @Min(1)
        private int requestsPerMinute = 200;  // Per internal service
        
        @Min(1)
        private int burstCapacity = 300;
    }
}