package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "firebase")
@Data
public class FirebaseProperties {
     private boolean enabled = false;
    
    private String credentialsPath;
    
    private String projectId;
    
    private String databaseUrl;
    
    private String storageBucket;
}