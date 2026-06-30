package com.aigreentick.services.notification.dto.response;

import java.time.Instant;
import java.util.List;

import com.aigreentick.services.notification.enums.NotificationStatus;
import com.aigreentick.services.notification.enums.email.EmailProviderType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationResponse {
    
    private String id;
    private List<String> to;
    private String subject;
    private NotificationStatus status;
    private EmailProviderType providerType;
    private Integer retryCount;
    private String errorMessage;
    private Long processingTimeMs;
    private Instant createdAt;
    private Instant updatedAt;
}