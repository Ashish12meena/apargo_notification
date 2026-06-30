package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "email.provider.smtp")
@Data
@Validated
public class SmtpProviderProperties {
    private int priority = 10;
    private boolean enabled = true;
    
}
