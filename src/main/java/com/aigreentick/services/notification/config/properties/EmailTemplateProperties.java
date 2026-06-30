package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "email.template")
@Data
public class EmailTemplateProperties {
    private String prefix = "templates/email/";
    
    private String suffix = ".html";
    
    private String basePath = "classpath:/templates/";

    private String mode = "HTML";

    private String encoding = "UTF-8";
    
  
}
