package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "spring.data.mongodb.collections")
@Data
public class MongoCollectionProperties {
    private String emailNotification = "email_notification";
    private String emailTemplate = "email_template";
    private String pushNotification = "push_notification";
    private String notificationAudit = "notification_audit";
}