package com.aigreentick.services.notification.service.push.impl;

import java.time.Instant;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.notification.dto.request.push.PushNotificationRequest;
import com.aigreentick.services.notification.enums.AuditEventType;
import com.aigreentick.services.notification.enums.NotificationStatus;
import com.aigreentick.services.notification.event.audit.AuditEvent;
import com.aigreentick.services.notification.event.audit.AuditEventPublisher;
import com.aigreentick.services.notification.exceptions.PushNotificationException;
import com.aigreentick.services.notification.model.entity.DeviceToken;
import com.aigreentick.services.notification.model.entity.PushNotification;
import com.aigreentick.services.notification.provider.push.PushProviderStrategy;
import com.aigreentick.services.notification.provider.selector.PushProviderSelector;
import com.aigreentick.services.notification.service.batch.BatchPushNotificationWriter;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeliveryServiceImpl {
    
    private final PushProviderSelector providerSelector;
    private final PushNotificationServiceImpl pushNotificationService;
    private final DeviceTokenService deviceTokenService;
    private final BatchPushNotificationWriter batchWriter;
    private final AuditEventPublisher auditPublisher;
    
    @Transactional
    @Retry(name = "emailRetry", fallbackMethod = "deliverFallback")
    public PushNotification deliver(PushNotificationRequest request, DeviceToken deviceToken) {
        PushProviderStrategy provider = providerSelector.selectProviderByPlatform(deviceToken.getPlatform());
        return executeDelivery(request, deviceToken, provider, null);
    }
    
    @Async("pushTaskExecutor")
    @Retry(name = "emailRetry", fallbackMethod = "deliverAsyncFallback")
    public void deliverAsync(PushNotificationRequest request, DeviceToken deviceToken, 
                            String notificationId) {
        log.info("Starting async push delivery for notification: {}", notificationId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            updateNotificationStatus(notificationId, NotificationStatus.PROCESSING);
            
            PushProviderStrategy provider = providerSelector.selectProviderByPlatform(deviceToken.getPlatform());
            provider.send(request);
            
            long processingTime = System.currentTimeMillis() - startTime;
            updateNotificationSuccess(notificationId, provider.getProviderType(), processingTime);
            
            log.info("Async push delivered successfully in {}ms for notification: {} via {}", 
                    processingTime, notificationId, provider.getProviderType());
            
            publishSuccessAudit(notificationId, request, deviceToken, processingTime);
            
        } catch (Exception e) {
            log.error("Async push delivery failed for notification: {}", notificationId, e);
            
            if (isInvalidTokenError(e)) {
                log.warn("Invalid device token, deactivating: {}", deviceToken.getDeviceToken());
                deviceTokenService.deactivateToken(deviceToken.getDeviceToken());
            }
            
            updateNotificationFailure(notificationId, e.getMessage());
            throw new PushNotificationException("Async push delivery failed", e);
        }
    }
    
    public PushNotification createPendingNotification(PushNotificationRequest request, 
                                                     DeviceToken deviceToken) {
        PushNotification notification = PushNotification.builder()
                .userId(deviceToken.getUserId())
                .deviceTokenId(deviceToken.getId())
                .deviceToken(deviceToken.getDeviceToken())
                .platform(deviceToken.getPlatform())
                .title(request.getTitle())
                .body(request.getBody())
                .data(request.getData())
                .imageUrl(request.getImageUrl())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
        
        notification = pushNotificationService.save(notification);
        log.info("Created PENDING push notification: {}", notification.getId());
        
        return notification;
    }
    
    private PushNotification executeDelivery(PushNotificationRequest request, 
                                            DeviceToken deviceToken,
                                            PushProviderStrategy provider,
                                            String existingNotificationId) {
        PushNotification notification;
        
        if (existingNotificationId != null) {
            notification = pushNotificationService.findOptionalById(existingNotificationId)
                    .orElseThrow(() -> new PushNotificationException(
                            "Notification not found: " + existingNotificationId));
        } else {
            notification = createNotificationRecord(request, deviceToken, provider);
        }
        
        try {
            provider.send(request);
            
            notification.setStatus(NotificationStatus.SENT);
            notification.setUpdatedAt(Instant.now());
            
            log.info("Push notification delivered successfully to: {} via {} for platform: {}",
                    deviceToken.getDeviceToken(), provider.getProviderType(), deviceToken.getPlatform());
            
        } catch (Exception e) {
            log.error("Failed to deliver push via provider: {}", provider.getProviderType(), e);
            
            if (isInvalidTokenError(e)) {
                deviceTokenService.deactivateToken(deviceToken.getDeviceToken());
            }
            
            notification.setStatus(NotificationStatus.FAILED);
            notification.setUpdatedAt(Instant.now());
            
            throw new PushNotificationException("Failed to deliver push notification", e);
        } finally {
            persistNotificationAsync(notification);
        }
        
        return notification;
    }
    
    private PushNotification createNotificationRecord(PushNotificationRequest request,
                                                     DeviceToken deviceToken,
                                                     PushProviderStrategy provider) {
        return PushNotification.builder()
                .userId(deviceToken.getUserId())
                .deviceTokenId(deviceToken.getId())
                .deviceToken(deviceToken.getDeviceToken())
                .platform(deviceToken.getPlatform())
                .title(request.getTitle())
                .body(request.getBody())
                .data(request.getData())
                .imageUrl(request.getImageUrl())
                .status(NotificationStatus.PROCESSING)
                .providerType(provider.getProviderType())
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }
    
    private void updateNotificationStatus(String notificationId, NotificationStatus status) {
        pushNotificationService.findOptionalById(notificationId).ifPresent(notification -> {
            notification.setStatus(status);
            notification.setUpdatedAt(Instant.now());
            pushNotificationService.save(notification);
        });
    }
    
    private void updateNotificationSuccess(String notificationId, 
                                          com.aigreentick.services.notification.enums.push.PushProviderType providerType,
                                          long processingTimeMs) {
        pushNotificationService.findOptionalById(notificationId).ifPresent(notification -> {
            notification.setStatus(NotificationStatus.SENT);
            notification.setProviderType(providerType);
            notification.setUpdatedAt(Instant.now());
            pushNotificationService.save(notification);
        });
    }
    
    private void updateNotificationFailure(String notificationId, String errorMessage) {
        pushNotificationService.findOptionalById(notificationId).ifPresent(notification -> {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setUpdatedAt(Instant.now());
            
            Integer retryCount = notification.getRetryCount();
            notification.setRetryCount(retryCount != null ? retryCount + 1 : 1);
            
            pushNotificationService.save(notification);
        });
    }
    
    private PushNotification persistNotificationAsync(PushNotification notification) {
        try {
            boolean enqueued = batchWriter.enqueue(notification);
            
            if (!enqueued) {
                return pushNotificationService.save(notification);
            }
            
            return notification;
            
        } catch (Exception e) {
            log.error("Error enqueueing push notification, saving synchronously", e);
            return pushNotificationService.save(notification);
        }
    }
    
    private boolean isInvalidTokenError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return message.contains("invalid") || 
               message.contains("unregistered") ||
               message.contains("token");
    }
    
    private void publishSuccessAudit(String notificationId, 
                                    PushNotificationRequest request,
                                    DeviceToken deviceToken,
                                    long processingTime) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .eventType(AuditEventType.NOTIFICATION_PROCESSED)
                    .serviceName("notification-service")
                    .entityId(notificationId)
                    .entityType("PUSH_NOTIFICATION")
                    .action("SEND_PUSH")
                    .status("SUCCESS")
                    .metadata(Map.of(
                        "userId", deviceToken.getUserId(),
                        "platform", deviceToken.getPlatform().name(),
                        "title", request.getTitle(),
                        "processingTimeMs", processingTime
                    ))
                    .build();
            
            auditPublisher.publish(event);
        } catch (Exception e) {
            log.error("Failed to publish push success audit event", e);
        }
    }
    
    @SuppressWarnings("unused")
    private PushNotification deliverFallback(PushNotificationRequest request, 
                                            DeviceToken deviceToken, Exception ex) {
        log.error("All retry attempts exhausted for push notification. Error: {}", 
                ex.getMessage());
        
        PushProviderStrategy provider = providerSelector.selectProviderByPlatform(deviceToken.getPlatform());
        PushNotification notification = createNotificationRecord(request, deviceToken, provider);
        
        notification.setStatus(NotificationStatus.FAILED);
        
        return pushNotificationService.save(notification);
    }
    
    @SuppressWarnings("unused")
    private void deliverAsyncFallback(PushNotificationRequest request, 
                                     DeviceToken deviceToken,
                                     String notificationId, Exception ex) {
        log.error("Async push delivery fallback triggered for notification: {}. Error: {}", 
                notificationId, ex.getMessage());
        
        updateNotificationFailure(notificationId, 
                "All retry attempts failed: " + ex.getMessage());
    }
}