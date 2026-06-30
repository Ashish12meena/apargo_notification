package com.aigreentick.services.notification.dto.response.push;

import java.time.Instant;

import com.aigreentick.services.notification.enums.NotificationStatus;
import com.aigreentick.services.notification.enums.push.DevicePlatform;
import com.aigreentick.services.notification.enums.push.PushProviderType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationResponse {
    private String id;
    private String userId;
    private String title;
    private String body;
    private DevicePlatform platform;
    private NotificationStatus status;
    private PushProviderType providerType;
    private String providerId;
    private Integer retryCount;
    private Instant createdAt;
    private Instant updatedAt;
}