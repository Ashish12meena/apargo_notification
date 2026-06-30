package com.aigreentick.services.notification.dto.response;

import java.time.Instant;

import com.aigreentick.services.notification.enums.NotificationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for async email requests
 * Returns immediately with tracking information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncEmailResponse {
    
    /**
     * Notification ID for status tracking
     */
    private String notificationId;
    
    /**
     * Current status (will be PENDING or PROCESSING initially)
     */
    private NotificationStatus status;
    
    /**
     * Message to the client
     */
    private String message;
    
    /**
     * Timestamp when request was accepted
     */
    private Instant acceptedAt;
    
    /**
     * Estimated processing time in seconds (optional)
     */
    private Integer estimatedProcessingTimeSeconds;
    
    /**
     * URL to check status (optional - can be constructed by client)
     */
    private String statusCheckUrl;

    /**
     * Queue position (optional - useful for visibility)
     */
    private Integer queuePosition;
}