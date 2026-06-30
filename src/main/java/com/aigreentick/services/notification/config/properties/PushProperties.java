package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import com.aigreentick.services.notification.enums.push.PushProviderType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "push")
@Data
@Validated
public class PushProperties {
    
    private PushProviderType active = PushProviderType.FCM;
    
    private FcmConfig fcm = new FcmConfig();
    private ApnsConfig apns = new ApnsConfig();
    private WebConfig web = new WebConfig();
    private ValidationConfig validation = new ValidationConfig();
    private RetryConfig retry = new RetryConfig();
    
    @Data
    public static class FcmConfig {
        private boolean enabled = true;
        
        @Min(1)
        @Max(100)
        private int priority = 10;
        
        private String projectId;
        private String credentialsPath;
        
        @Min(1000)
        private int timeout = 30000;
        
        private boolean dryRun = false;
    }
    
    @Data
    public static class ApnsConfig {
        private boolean enabled = false;
        
        @Min(1)
        @Max(100)
        private int priority = 5;
        
        private String teamId;
        private String keyId;
        private String keyPath;
        private String bundleId;
        private boolean production = false;
        
        @Min(1000)
        private int timeout = 30000;
    }
    
    @Data
    public static class WebConfig {
        private boolean enabled = false;
        
        @Min(1)
        @Max(100)
        private int priority = 3;
        
        private String vapidPublicKey;
        private String vapidPrivateKey;
        private String subject;
        
        @Min(1000)
        private int timeout = 30000;
        
        @Min(0)
        private int defaultTtl = 86400; // 24 hours
    }
    
    @Data
    public static class ValidationConfig {
        private boolean enabled = true;
        
        @Min(1)
        @Max(1000)
        private int maxRecipients = 100;
        
        @Min(1)
        @Max(10)
        private int maxTitleLength = 65;
        
        @Min(1)
        @Max(500)
        private int maxBodyLength = 240;
        
        @Min(1)
        @Max(100)
        private int maxDataPayloadKb = 4;
    }
    
    @Data
    public static class RetryConfig {
        private boolean enabled = true;
        
        @Min(1)
        @Max(10)
        private int maxAttempts = 3;
        
        @Min(100)
        private long initialDelayMs = 1000;
        
        @Min(1)
        private double multiplier = 2.0;
        
        @Min(1000)
        private long maxDelayMs = 30000;
    }
}