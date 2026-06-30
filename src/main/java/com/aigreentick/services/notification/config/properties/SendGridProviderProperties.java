package com.aigreentick.services.notification.config.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "email.provider.sendgrid")
@Data
@Validated
public class SendGridProviderProperties {
    
    private boolean enabled = false;
    
    @Min(1)
    @Max(100)
    private int priority = 5;
    
    private String apiKey;
    
    @Min(1000)
    private int timeout = 30000;
}
