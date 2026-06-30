package com.aigreentick.services.notification.event.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publisher for audit events
 * Use this to publish audit events from anywhere in your application
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Publish an audit event
     * This is non-blocking and returns immediately
     * 
     * @param event The audit event to publish
     */
    public void publish(AuditEvent event) {
        try {
            log.debug("Publishing audit event: type={}, entityId={}, action={}", 
                     event.getEventType(), 
                     event.getEntityId(), 
                     event.getAction());
            
            eventPublisher.publishEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to publish audit event: {}", event, e);
        }
    }
}