package com.aigreentick.services.notification.mapper;

import org.springframework.stereotype.Component;

import com.aigreentick.services.notification.dto.request.push.PushNotificationRequest;
import com.aigreentick.services.notification.dto.request.push.SendPushRequest;
import com.aigreentick.services.notification.dto.response.push.PushNotificationResponse;
import com.aigreentick.services.notification.model.entity.PushNotification;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PushNotificationMapper {
    
    public PushNotificationRequest toPushRequest(SendPushRequest request) {
        return PushNotificationRequest.builder()
                .deviceToken(request.getDeviceToken())
                .title(request.getTitle())
                .body(request.getBody())
                .data(request.getData())
                .imageUrl(request.getImageUrl())
                .sound(request.getSound())
                .badge(request.getBadge())
                .build();
    }
    
    public PushNotificationResponse toResponse(PushNotification notification) {
        return PushNotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .platform(notification.getPlatform())
                .status(notification.getStatus())
                .providerType(notification.getProviderType())
                .providerId(notification.getProviderId())
                .retryCount(notification.getRetryCount())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}