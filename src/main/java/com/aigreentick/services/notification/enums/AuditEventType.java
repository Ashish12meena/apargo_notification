package com.aigreentick.services.notification.enums;


/**
 * Types of audit events
 */
public enum AuditEventType {
    // Email Events
    EMAIL_SENT,
    EMAIL_FAILED,
    EMAIL_RETRY,
    
    // Template Events
    TEMPLATE_CREATED,
    TEMPLATE_UPDATED,
    TEMPLATE_DELETED,
    TEMPLATE_ACTIVATED,
    TEMPLATE_DEACTIVATED,
    
    // System Events
    RATE_LIMIT_EXCEEDED,
    CIRCUIT_BREAKER_OPENED,
    PROVIDER_FAILED,
    
    // Generic
    NOTIFICATION_CREATED,
    NOTIFICATION_PROCESSED
}