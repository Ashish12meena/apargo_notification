package com.aigreentick.services.notification.service.email.impl;

import java.time.Instant;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.notification.config.properties.EmailProperties;
import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;
import com.aigreentick.services.notification.enums.AuditEventType;
import com.aigreentick.services.notification.enums.NotificationStatus;
import com.aigreentick.services.notification.enums.email.EmailProviderType;
import com.aigreentick.services.notification.event.audit.AuditEvent;
import com.aigreentick.services.notification.event.audit.AuditEventPublisher;
import com.aigreentick.services.notification.exceptions.NotificationSendException;
import com.aigreentick.services.notification.model.entity.EmailNotification;
import com.aigreentick.services.notification.provider.email.EmailProviderStrategy;
import com.aigreentick.services.notification.provider.selector.EmailProviderSelector;
import com.aigreentick.services.notification.service.batch.BatchEmailNotificationWriter;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryServiceImpl {
    
    private final EmailProviderSelector providerSelector;
    private final EmailNotificationServiceImpl emailNotificationService;
    private final EmailProperties emailProperties;
    private final BatchEmailNotificationWriter batchWriter;
    private final AuditEventPublisher auditPublisher;

    // ==================== SYNCHRONOUS DELIVERY ====================

    /**
     * Deliver email synchronously with retry
     */
    @Transactional
    @Retry(name = "emailRetry", fallbackMethod = "deliverFallback")
    public EmailNotification deliver(EmailNotificationRequest request) {
        EmailProviderStrategy provider = providerSelector.selectProvider();
        return executeDelivery(request, provider, null);
    }

    // ==================== ASYNCHRONOUS DELIVERY ====================

    /**
     * Deliver email asynchronously (fire-and-forget)
     * 
     * This method:
     * 1. Runs in background thread pool
     * 2. Updates notification status in database
     * 3. Does NOT block the caller
     * 
     * @param request Email request
     * @param notificationId Pre-created notification ID
     */
    @Async("emailTaskExecutor")
    @Retry(name = "emailRetry", fallbackMethod = "deliverAsyncFallback")
    public void deliverAsync(EmailNotificationRequest request, String notificationId) {
        log.info("Starting async delivery for notification: {}", notificationId);
        
        long startTime = System.currentTimeMillis();

        try {
            updateNotificationStatus(notificationId, NotificationStatus.PROCESSING);

            EmailProviderStrategy provider = providerSelector.selectProvider();
            provider.send(request);

            long processingTime = System.currentTimeMillis() - startTime;
            updateNotificationSuccess(notificationId, provider.getProviderType(), processingTime);

            log.info("Async email delivered successfully in {}ms for notification: {}", 
                    processingTime, notificationId);
             publishSuccessAudit(notificationId, request, processingTime);

        } catch (Exception e) {
            log.error("Async email delivery failed for notification: {}", notificationId, e);
            updateNotificationFailure(notificationId, e.getMessage());
            throw new NotificationSendException("Async email delivery failed", e);
        }
    }

    /**
     * Fallback for async delivery failures
     */
    @SuppressWarnings("unused")
    private void deliverAsyncFallback(EmailNotificationRequest request, String notificationId, 
            Exception ex) {
        log.error("Async delivery fallback triggered for notification: {}. Error: {}", 
                notificationId, ex.getMessage());
        
        updateNotificationFailure(notificationId, 
                "All retry attempts failed: " + ex.getMessage());
    }

    // ==================== NOTIFICATION MANAGEMENT ====================
 
    /**
     * Create notification record in PENDING status
     * Called before async processing
     */
    public EmailNotification createPendingNotification(EmailNotificationRequest request) {
        EmailNotification notification = EmailNotification.builder()
                .to(request.getTo())
                .from(emailProperties.getFromEmail())
                .cc(request.getCc())
                .bcc(request.getBcc())
                .subject(request.getSubject())
                .body(request.getBody())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        notification = emailNotificationService.save(notification);
        log.info("Created PENDING notification: {}", notification.getId());
        
        return notification;
    }

    /**
     * Update notification status
     */
    private void updateNotificationStatus(String notificationId, NotificationStatus status) {
        emailNotificationService.findOptionalById(notificationId).ifPresent(notification -> {
            notification.setStatus(status);
            notification.setUpdatedAt(Instant.now());
            emailNotificationService.save(notification);
        });
    }

    /**
     * Update notification on successful delivery
     */
    private void updateNotificationSuccess(String notificationId, 
            EmailProviderType providerType, long processingTimeMs) {
        
        emailNotificationService.findOptionalById(notificationId).ifPresent(notification -> {
            notification.setStatus(NotificationStatus.SENT);
            notification.setProviderType(providerType);
            notification.setUpdatedAt(Instant.now());
            emailNotificationService.save(notification);
        });
    }

    /**
     * Update notification on failure
     */
    private void updateNotificationFailure(String notificationId, String errorMessage) {
        emailNotificationService.findOptionalById(notificationId).ifPresent(notification -> {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setUpdatedAt(Instant.now());
            
            Integer retryCount = notification.getRetryCount();
            notification.setRetryCount(retryCount != null ? retryCount + 1 : 1);
            
            emailNotificationService.save(notification);
        });
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Execute delivery (used by synchronous flow)
     */
    private EmailNotification executeDelivery(
            EmailNotificationRequest request,
            EmailProviderStrategy provider,
            String existingNotificationId) {

        EmailNotification notification;
        
        if (existingNotificationId != null) {
            // Use existing notification record
            notification = emailNotificationService.findOptionalById(existingNotificationId)
                    .orElseThrow(() -> new NotificationSendException(
                            "Notification not found: " + existingNotificationId));
        } else {
            // Create new notification record
            notification = createNotificationRecord(request, provider.getProviderType());
        }

        try {
            provider.send(request);

            notification.setStatus(NotificationStatus.SENT);
            notification.setUpdatedAt(Instant.now());

            log.info("Email delivered successfully to: {} via {}",
                    request.getTo(), provider.getProviderType());

        } catch (Exception e) {
            log.error("Failed to deliver email via provider: {}", provider.getProviderType(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setUpdatedAt(Instant.now());
            throw new NotificationSendException("Failed to deliver email", e);
        } finally {
            persistNotificationAsync(notification);
        }

        return notification;
    }

    /**
     * Create notification record (for sync flow)
     */
    private EmailNotification createNotificationRecord(
            EmailNotificationRequest request,
            EmailProviderType providerType) {
        
        return EmailNotification.builder()
                .to(request.getTo())
                .from(emailProperties.getFromEmail())
                .cc(request.getCc())
                .bcc(request.getBcc())
                .subject(request.getSubject())
                .body(request.getBody())
                .status(NotificationStatus.PROCESSING)
                .providerType(providerType)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Persist notification using batch writer
     */
    private EmailNotification persistNotificationAsync(EmailNotification notification) {
        try {
            boolean enqueued = batchWriter.enqueue(notification);
            
            if (!enqueued) {
                return emailNotificationService.save(notification);
            }
            
            return notification;

        } catch (Exception e) {
            log.error("Error enqueueing notification, saving synchronously", e);
            return emailNotificationService.save(notification);
        }
    }

    /**
     * Fallback for synchronous delivery
     */
    @SuppressWarnings("unused")
    private EmailNotification deliverFallback(EmailNotificationRequest request, Exception ex) {
        log.error("All retry attempts exhausted. Creating FAILED notification. Error: {}", 
                ex.getMessage());
        
        EmailNotification notification = createNotificationRecord(
                request, 
                EmailProviderType.SMTP);
        
        notification.setStatus(NotificationStatus.FAILED);
        
        return emailNotificationService.save(notification);
    }

    /**
     * Publish audit event for successful email delivery
     */
    private void publishSuccessAudit(String notificationId, 
                                     EmailNotificationRequest request, 
                                     long processingTime) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .eventType(AuditEventType.EMAIL_SENT)
                    .serviceName("notification-service")
                    .entityId(notificationId)
                    .entityType("EMAIL_NOTIFICATION")
                    .action("SEND_EMAIL")
                    .status("SUCCESS")
                    .metadata(Map.of(
                        "recipients", request.getTo(),
                        "subject", request.getSubject(),
                        "processingTimeMs", processingTime
                    ))
                    .build();
            
            auditPublisher.publish(event);
        } catch (Exception e) {
            log.error("Failed to publish success audit event", e);
        }
    }
}