package com.aigreentick.services.notification.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.notification.dto.request.push.DeviceTokenRequest;
import com.aigreentick.services.notification.dto.request.push.SendPushRequest;
import com.aigreentick.services.notification.dto.response.push.AsyncPushResponse;
import com.aigreentick.services.notification.dto.response.push.DeviceTokenResponse;
import com.aigreentick.services.notification.dto.response.push.PushNotificationResponse;
import com.aigreentick.services.notification.service.push.impl.DeviceTokenService;
import com.aigreentick.services.notification.service.push.impl.PushOrchestratorServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification/push")
public class PushNotificationController {
    
    private final PushOrchestratorServiceImpl pushOrchestratorService;
    private final DeviceTokenService deviceTokenService;
    
    // ==================== DEVICE TOKEN MANAGEMENT ====================
    
    @PostMapping("/device/register")
    public ResponseEntity<DeviceTokenResponse> registerDevice(
            @Valid @RequestBody DeviceTokenRequest request) {
        
        log.info("Received request to register device token for user: {}", request.getUserId());
        
        DeviceTokenResponse response = deviceTokenService.registerToken(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/device/user/{userId}")
    public ResponseEntity<List<DeviceTokenResponse>> getUserDevices(
            @PathVariable String userId) {
        
        log.info("Fetching device tokens for user: {}", userId);
        
        List<DeviceTokenResponse> response = deviceTokenService.getUserTokens(userId);
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/device/{deviceToken}")
    public ResponseEntity<Void> deactivateDevice(@PathVariable String deviceToken) {
        log.info("Deactivating device token: {}", deviceToken);
        
        deviceTokenService.deactivateToken(deviceToken);
        
        return ResponseEntity.noContent().build();
    }
    
    // ==================== PUSH NOTIFICATION SENDING ====================
    
    @PostMapping("/send")
    public ResponseEntity<PushNotificationResponse> sendPush(
            @Valid @RequestBody SendPushRequest request) {
        
        log.info("Received SYNC request to send push notification");
        
        PushNotificationResponse response = pushOrchestratorService.sendPush(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/send/async")
    public ResponseEntity<AsyncPushResponse> sendPushAsync(
            @Valid @RequestBody SendPushRequest request) {
        
        log.info("Received ASYNC request to send push notification");
        
        AsyncPushResponse response = pushOrchestratorService.sendPushAsync(request);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    @PostMapping("/send/user")
    public ResponseEntity<List<AsyncPushResponse>> sendPushToUser(
            @Valid @RequestBody SendPushRequest request) {
        
        log.info("Received request to send push to all user devices");
        
        List<AsyncPushResponse> response = pushOrchestratorService.sendPushToUser(request);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    // ==================== STATUS CHECKING ====================
    
    @GetMapping("/status/{notificationId}")
    public ResponseEntity<PushNotificationResponse> getPushStatus(
            @PathVariable String notificationId) {
        
        log.info("Checking status for push notification: {}", notificationId);
        
        PushNotificationResponse response = pushOrchestratorService.getPushStatus(notificationId);
        
        return ResponseEntity.ok(response);
    }
}