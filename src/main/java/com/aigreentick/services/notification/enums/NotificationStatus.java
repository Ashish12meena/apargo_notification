package com.aigreentick.services.notification.enums;

/**
 * Enum representing the lifecycle status of a notification
 */
public enum NotificationStatus {
    PENDING,
    
    PROCESSING,
    
    SENT,
    
    DELIVERED,
    
    FAILED,
  
    RETRYING,
    
    BOUNCED,
    
    SPAM_COMPLAINT,
    
    CANCELLED,
    
    EXPIRED
}