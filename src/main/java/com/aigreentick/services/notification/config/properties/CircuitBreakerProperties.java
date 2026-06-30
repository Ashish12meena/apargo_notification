package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "resilience4j.circuitbreaker")
@Data
@Validated
public class CircuitBreakerProperties {
    
    private ConfigsProperties configs = new ConfigsProperties();
    private InstancesProperties instances = new InstancesProperties();
    
    /**
     * Configs section - contains default and other shared configurations
     */
    @Data
    public static class ConfigsProperties {
        private DefaultConfig defaultConfig = new DefaultConfig();
        
        public DefaultConfig getDefault() {
            return defaultConfig;
        }
        
        public void setDefault(DefaultConfig defaultConfig) {
            this.defaultConfig = defaultConfig;
        }
    }
    
    /**
     * Default Circuit Breaker Configuration
     */
    @Data
    public static class DefaultConfig {
        private boolean registerHealthIndicator = true;
        private String slidingWindowType = "COUNT_BASED";
        
        @Min(1)
        @Max(100)
        private int slidingWindowSize = 10;
        
        @Min(1)
        @Max(20)
        private int minimumNumberOfCalls = 5;
        
        @Min(1)
        @Max(10)
        private int permittedNumberOfCallsInHalfOpenState = 3;
        
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
        private String waitDurationInOpenState = "60s";
        
        @Min(0)
        @Max(100)
        private int failureRateThreshold = 50;
        
        @Min(0)
        @Max(100)
        private int slowCallRateThreshold = 100;
        
        private String slowCallDurationThreshold = "5s";
    }
    
    /**
     * Instances section - contains specific circuit breaker instances
     */
    @Data
    public static class InstancesProperties {
        private EmailProviderConfig emailProvider = new EmailProviderConfig();
        
    }
    
    /**
     * Email Provider Circuit Breaker Instance Configuration
     */
    @Data
    public static class EmailProviderConfig {
        private String baseConfig = "default";
        
        @Min(1)
        @Max(100)
        private int slidingWindowSize = 10;
        
        @Min(1)
        @Max(20)
        private int minimumNumberOfCalls = 5;
        
        @Min(0)
        @Max(100)
        private int failureRateThreshold = 50;
        
        @Min(0)
        @Max(100)
        private int slowCallRateThreshold = 100;
        
        @Min(1000)
        private long slowCallDurationThresholdMs = 5000;
        
        @Min(1000)
        private long waitDurationInOpenStateMs = 30000;
        
        @Min(1)
        @Max(10)
        private int permittedNumberOfCallsInHalfOpenState = 3;
        
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
    }
}