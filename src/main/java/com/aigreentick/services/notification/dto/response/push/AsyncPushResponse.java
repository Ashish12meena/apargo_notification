package com.aigreentick.services.notification.dto.response.push;

import java.time.Instant;

import com.aigreentick.services.notification.enums.NotificationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncPushResponse {
    private String notificationId;
    private NotificationStatus status;
    private String message;
    private Instant acceptedAt;
    private Integer estimatedProcessingTimeSeconds;
    private String statusCheckUrl;
}