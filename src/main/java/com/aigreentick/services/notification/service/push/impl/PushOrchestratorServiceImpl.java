package com.aigreentick.services.notification.service.push.impl;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.dto.request.push.SendPushRequest;
import com.aigreentick.services.notification.dto.response.push.AsyncPushResponse;
import com.aigreentick.services.notification.dto.response.push.PushNotificationResponse;
import com.aigreentick.services.notification.enums.NotificationStatus;
import com.aigreentick.services.notification.exceptions.DeviceTokenNotFoundException;
import com.aigreentick.services.notification.mapper.PushNotificationMapper;
import com.aigreentick.services.notification.model.entity.DeviceToken;
import com.aigreentick.services.notification.model.entity.PushNotification;
import com.aigreentick.services.notification.validator.PushValidationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushOrchestratorServiceImpl {
    
    private final PushDeliveryServiceImpl pushDeliveryService;
    private final DeviceTokenService deviceTokenService;
    private final PushNotificationServiceImpl pushNotificationService;
    private final PushValidationService validationService;
    private final PushNotificationMapper mapper;
    
    public PushNotificationResponse sendPush(SendPushRequest request) {
        log.info("Orchestrating SYNC push send");
        
        validationService.validateSendRequest(request);
        
        DeviceToken deviceToken = resolveDeviceToken(request);
        
        var pushRequest = mapper.toPushRequest(request);
        
        PushNotification notification = pushDeliveryService.deliver(pushRequest, deviceToken);
        
        return mapper.toResponse(notification);
    }
    
    public AsyncPushResponse sendPushAsync(SendPushRequest request) {
        log.info("Orchestrating ASYNC push send");
        
        validationService.validateSendRequest(request);
        
        DeviceToken deviceToken = resolveDeviceToken(request);
        
        var pushRequest = mapper.toPushRequest(request);
        
        PushNotification notification = pushDeliveryService.createPendingNotification(
                pushRequest, deviceToken);
        
        pushDeliveryService.deliverAsync(pushRequest, deviceToken, notification.getId());
        
        return AsyncPushResponse.builder()
                .notificationId(notification.getId())
                .status(NotificationStatus.PENDING)
                .message("Push notification accepted for processing")
                .acceptedAt(Instant.now())
                .estimatedProcessingTimeSeconds(3)
                .statusCheckUrl("/api/v1/notification/push/status/" + notification.getId())
                .build();
    }
    
    public List<AsyncPushResponse> sendPushToUser(SendPushRequest request) {
        log.info("Sending push to all devices for user: {}", request.getUserId());
        
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required for user-based push");
        }
        
        List<DeviceToken> userTokens = deviceTokenService.getUserTokens(request.getUserId())
                .stream()
                .map(response -> deviceTokenService.getActiveTokenByValue(response.getDeviceToken()))
                .collect(Collectors.toList());
        
        if (userTokens.isEmpty()) {
            throw new DeviceTokenNotFoundException(
                    "No active device tokens found for user: " + request.getUserId());
        }
        
        return userTokens.stream()
                .map(deviceToken -> {
                    var pushRequest = mapper.toPushRequest(request);
                    
                    PushNotification notification = pushDeliveryService.createPendingNotification(
                            pushRequest, deviceToken);
                    
                    pushDeliveryService.deliverAsync(pushRequest, deviceToken, notification.getId());
                    
                    return AsyncPushResponse.builder()
                            .notificationId(notification.getId())
                            .status(NotificationStatus.PENDING)
                            .message("Push notification accepted")
                            .acceptedAt(Instant.now())
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    public PushNotificationResponse getPushStatus(String notificationId) {
        log.debug("Fetching status for push notification: {}", notificationId);
        
        PushNotification notification = pushNotificationService.findOptionalById(notificationId)
                .orElseThrow(() -> new DeviceTokenNotFoundException(
                        "Push notification not found: " + notificationId));
        
        return mapper.toResponse(notification);
    }
    
    private DeviceToken resolveDeviceToken(SendPushRequest request) {
        if (request.getDeviceToken() != null) {
            return deviceTokenService.getActiveTokenByValue(request.getDeviceToken());
        }
        
        if (request.getUserId() != null) {
            List<DeviceToken> tokens = deviceTokenService.getUserTokens(request.getUserId())
                    .stream()
                    .map(response -> deviceTokenService.getActiveTokenByValue(
                            response.getDeviceToken()))
                    .collect(Collectors.toList());
            
            if (tokens.isEmpty()) {
                throw new DeviceTokenNotFoundException(
                        "No active device tokens found for user: " + request.getUserId());
            }
            
            return tokens.get(0);
        }
        
        throw new IllegalArgumentException("Either deviceToken or userId must be provided");
    }
}