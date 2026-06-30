package com.aigreentick.services.notification.model.entity;

import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;

import com.aigreentick.services.notification.enums.NotificationStatus;
import com.aigreentick.services.notification.enums.push.DevicePlatform;
import com.aigreentick.services.notification.enums.push.PushProviderType;
import com.aigreentick.services.notification.model.interfaces.MongoBaseEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Document(collection = "push_notification")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PushNotification extends MongoBaseEntity{
     private String userId;
    
    private String deviceTokenId;
    
    private String deviceToken;
    
    private DevicePlatform platform;
    
    private String title;
    
    private String body;
    
    private Map<String, String> data;
    
    private String imageUrl;
    
    private NotificationStatus status;
    
    private PushProviderType providerType;
    
    private String providerId;
    
    private Integer retryCount;
    
}
