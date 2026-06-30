package com.aigreentick.services.notification.client.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from audit service
 * Usually ignored in fire-and-forget scenarios
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResponse {
    
    private String auditId;
    private String status;
    private String message;
}