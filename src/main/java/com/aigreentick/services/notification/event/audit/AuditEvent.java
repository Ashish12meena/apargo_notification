package com.aigreentick.services.notification.event.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

import com.aigreentick.services.notification.enums.AuditEventType;

/**
 * Event published when audit data needs to be sent to audit service
 * This is a Spring Application Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    
    /**
     * Type of audit event (EMAIL_SENT, EMAIL_FAILED, etc.)
     */
    private AuditEventType eventType;
    
    /**
     * Service name that generated this event
     */
    private String serviceName;
    
    /**
     * Entity ID (e.g., notification ID)
     */
    private String entityId;
    
    /**
     * Entity type (e.g., EMAIL_NOTIFICATION)
     */
    private String entityType;
    
    /**
     * User ID who triggered the action (if applicable)
     */
    private String userId;
    
    /**
     * Action performed (e.g., "SEND_EMAIL", "CREATE_TEMPLATE")
     */
    private String action;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Timestamp when event occurred
     */
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    /**
     * Status (SUCCESS, FAILED, PENDING)
     */
    private String status;
    
    /**
     * Error message if failed
     */
    private String errorMessage;
}