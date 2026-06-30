package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "async")
@Data
@Validated
public class AsyncProperties {
    
    private EmailAsyncConfig email = new EmailAsyncConfig();
    private PushAsyncConfig push = new PushAsyncConfig();
    
    @Data
    public static class EmailAsyncConfig {
        private boolean enabled = true;
        
        @Min(1)
        private int corePoolSize = 5;
        
        @Min(1)
        private int maxPoolSize = 10;
        
        @Min(0)
        private int queueCapacity = 100;
        
        private String threadNamePrefix = "email-async-";
        
        @Min(10)
        private int keepAliveSeconds = 60;
        
        @Min(10)
        private int awaitTerminationSeconds = 60;
    }
    
    @Data
    public static class PushAsyncConfig {
        private boolean enabled = true;
        
        @Min(1)
        private int corePoolSize = 5;
        
        @Min(1)
        private int maxPoolSize = 10;
        
        @Min(0)
        private int queueCapacity = 100;
        
        private String threadNamePrefix = "push-async-";
        
        @Min(10)
        private int keepAliveSeconds = 60;
    }
}