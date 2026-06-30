package com.aigreentick.services.notification.client.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO sent to audit service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRequest {
    
    private String eventType;
    private String serviceName;
    private String entityId;
    private String entityType;
    private String userId;
    private String action;
    private Map<String, Object> metadata;
    private Instant timestamp;
    private String status;
    private String errorMessage;
}