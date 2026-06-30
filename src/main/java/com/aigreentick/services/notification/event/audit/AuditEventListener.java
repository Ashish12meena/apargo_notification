package com.aigreentick.services.notification.event.audit;

import com.aigreentick.services.notification.client.audit.AuditFeignClient;
import com.aigreentick.services.notification.client.audit.dto.AuditRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens to audit events and sends them to audit service
 * Runs in background thread - does NOT block main application flow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {
    
    private final AuditFeignClient auditClient;
    
    /**
     * Handle audit events asynchronously
     * This method runs in a background thread
     * 
     * @param event The audit event to process
     */
    @Async("emailTaskExecutor")  // Reuse existing thread pool
    @EventListener
    public void handleAuditEvent(AuditEvent event) {
        try {
            log.debug("Processing audit event: type={}, entityId={}", 
                     event.getEventType(), 
                     event.getEntityId());
            
            // Convert event to audit request
            AuditRequest request = convertToAuditRequest(event);
            
            // Call audit service via Feign
            auditClient.sendAudit(request);
            
            log.info("Successfully sent audit to audit service: entityId={}, action={}", 
                    event.getEntityId(), 
                    event.getAction());
            
        } catch (Exception e) {
            // Log error but don't throw - we don't want to affect main flow
            log.error("Failed to send audit to audit service: event={}", event, e);
            
            // Optional: You could add retry logic here or save to DB for later retry
        }
    }
    
    /**
     * Convert AuditEvent to AuditRequest DTO
     */
    private AuditRequest convertToAuditRequest(AuditEvent event) {
        return AuditRequest.builder()
                .eventType(event.getEventType() != null ? event.getEventType().name() : null)
                .serviceName(event.getServiceName())
                .entityId(event.getEntityId())
                .entityType(event.getEntityType())
                .userId(event.getUserId())
                .action(event.getAction())
                .metadata(event.getMetadata())
                .timestamp(event.getTimestamp())
                .status(event.getStatus())
                .errorMessage(event.getErrorMessage())
                .build();
    }
}