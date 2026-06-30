package com.aigreentick.services.notification.provider.push;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.config.properties.PushProperties;
import com.aigreentick.services.notification.dto.request.push.PushNotificationRequest;
import com.aigreentick.services.notification.enums.push.PushProviderType;
import com.aigreentick.services.notification.exceptions.PushNotificationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;

/**
 * Web Push Provider
 * Handles browser-based push notifications using Web Push Protocol
 * Uses VAPID authentication (Voluntary Application Server Identification)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "push.web", name = "enabled", havingValue = "true")
public class WebPushProvider implements PushProviderStrategy {

    private final PushProperties pushProperties;
    private final ObjectMapper objectMapper;
    private PushService pushService;


    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Web Push service...");
            
            Security.addProvider(new BouncyCastleProvider());
            
            validateWebPushConfiguration();
            
            PushProperties.WebConfig config = pushProperties.getWeb();
            
            pushService = new PushService(
                config.getVapidPublicKey(),
                config.getVapidPrivateKey(),
                config.getSubject()
            );
            
            log.info("Web Push service initialized successfully");
            
        } catch (GeneralSecurityException e) {
            log.error("Failed to initialize Web Push service - Security error", e);
            throw new PushNotificationException("Web Push initialization failed", e);
        } catch (Exception e) {
            log.error("Failed to initialize Web Push service", e);
            throw new PushNotificationException("Web Push initialization failed", e);
        }
    }

    @Override
    public void send(PushNotificationRequest request) {
        if (pushService == null) {
            throw new PushNotificationException("Web Push service not initialized");
        }

        log.info("Sending Web Push notification to subscription: {}...",
                request.getDeviceToken().substring(0, Math.min(10, request.getDeviceToken().length())));

        try {
            Subscription subscription = parseSubscription(request.getDeviceToken());
            String payload = buildWebPushPayload(request);
            
            Notification notification = new Notification(subscription, payload, determineUrgency(request));
            
            HttpResponse response = pushService.send(notification);
            
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode >= 200 && statusCode < 300) {
                log.info("Web Push notification sent successfully. Status: {}", statusCode);
            } else {
                log.error("Web Push notification failed. Status: {}", statusCode);
                handleWebPushError(statusCode);
            }
            
        } catch (GeneralSecurityException e) {
            log.error("Security error sending Web Push notification", e);
            throw new PushNotificationException("Failed to send Web Push notification", e);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Web Push payload", e);
            throw new PushNotificationException("Invalid Web Push payload", e);
        } catch (JoseException e) {
            log.error("JOSE error sending Web Push notification", e);
            throw new PushNotificationException("Failed to send Web Push notification", e);
        } catch (ExecutionException e) {
            log.error("Execution error sending Web Push notification", e);
            throw new PushNotificationException("Failed to send Web Push notification", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Web Push send interrupted", e);
            throw new PushNotificationException("Web Push send interrupted", e);
        } catch (Exception e) {
            log.error("Unexpected error sending Web Push notification", e);
            throw new PushNotificationException("Failed to send Web Push notification", e);
        }
    }

    private Subscription parseSubscription(String deviceToken) throws JsonProcessingException {
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> subscriptionMap = objectMapper.readValue(deviceToken, Map.class);
            
            String endpoint = (String) subscriptionMap.get("endpoint");
            
            @SuppressWarnings("unchecked")
            Map<String, String> keys = (Map<String, String>) subscriptionMap.get("keys");
            
            if (endpoint == null || keys == null) {
                throw new PushNotificationException("Invalid subscription format");
            }
            
            String p256dh = keys.get("p256dh");
            String auth = keys.get("auth");
            
            if (p256dh == null || auth == null) {
                throw new PushNotificationException("Missing subscription keys");
            }
            
            Subscription subscription = new Subscription();
            subscription.endpoint = endpoint;
            subscription.keys = new Subscription.Keys();
            subscription.keys.p256dh = p256dh;
            subscription.keys.auth = auth;
            
            return subscription;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse subscription from device token", e);
            throw new PushNotificationException("Invalid device token format", e);
        }
    }

    private String buildWebPushPayload(PushNotificationRequest request) 
            throws JsonProcessingException {
        
        Map<String, Object> payload = new HashMap<>();
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", request.getTitle());
        notification.put("body", request.getBody());
        
        if (request.getImageUrl() != null) {
            notification.put("icon", request.getImageUrl());
            notification.put("image", request.getImageUrl());
        }
        
        if (request.getBadge() != null) {
            notification.put("badge", request.getBadge());
        }
        
        if (request.getClickAction() != null) {
            notification.put("data", Map.of("url", request.getClickAction()));
        }
        
        notification.put("requireInteraction", false);
        
        notification.put("vibrate", new int[]{200, 100, 200});
        
        if (request.getSound() != null && "silent".equals(request.getSound())) {
            notification.put("silent", true);
        }
        
        notification.put("timestamp", System.currentTimeMillis());
        
        payload.put("notification", notification);
        
        if (request.getData() != null && !request.getData().isEmpty()) {
            payload.put("data", request.getData());
        }
        
        String jsonPayload = objectMapper.writeValueAsString(payload);
        
        log.debug("Built Web Push payload: {}", jsonPayload);
        
        return jsonPayload;
    }

    private Urgency determineUrgency(PushNotificationRequest request) {
        if (request.getPriority() != null) {
            if (request.getPriority() >= 10) {
                return Urgency.HIGH;
            } else if (request.getPriority() >= 5) {
                return Urgency.NORMAL;
            } else if (request.getPriority() >= 2) {
                return Urgency.LOW;
            } else {
                return Urgency.VERY_LOW;
            }
        }
        return Urgency.NORMAL;
    }

    private void handleWebPushError(int statusCode) {
        switch (statusCode) {
            case 400:
                throw new PushNotificationException("Bad request");
            
            case 401:
            case 403:
                throw new PushNotificationException("Authentication failed");
            
            case 404:
            case 410:
                // Subscription expired or not found
                throw new PushNotificationException("Invalid or expired subscription");
            
            case 413:
                throw new PushNotificationException("Payload too large");
            
            case 429:
                throw new PushNotificationException("Rate limit exceeded");
            
            case 500:
            case 502:
            case 503:
                throw new PushNotificationException("Push service unavailable");
            
            default:
                throw new PushNotificationException(
                    "Web Push failed with status: " + statusCode);
        }
    }

    private void validateWebPushConfiguration() {
        PushProperties.WebConfig config = pushProperties.getWeb();
        
        if (config.getVapidPublicKey() == null || config.getVapidPublicKey().isBlank()) {
            throw new PushNotificationException("Web Push VAPID public key not configured");
        }
        
        if (config.getVapidPrivateKey() == null || config.getVapidPrivateKey().isBlank()) {
            throw new PushNotificationException("Web Push VAPID private key not configured");
        }
        
        if (config.getSubject() == null || config.getSubject().isBlank()) {
            throw new PushNotificationException("Web Push subject not configured");
        }
    }

    @Override
    public PushProviderType getProviderType() {
        return PushProviderType.WEB_PUSH;
    }

    @Override
    public boolean isAvailable() {
        if (!pushProperties.getWeb().isEnabled()) {
            return false;
        }
        
        if (pushService == null) {
            return false;
        }
        
        try {
            PushProperties.WebConfig config = pushProperties.getWeb();
            
            boolean hasPublicKey = config.getVapidPublicKey() != null && 
                                  !config.getVapidPublicKey().isBlank();
            boolean hasPrivateKey = config.getVapidPrivateKey() != null && 
                                   !config.getVapidPrivateKey().isBlank();
            boolean hasSubject = config.getSubject() != null && 
                                !config.getSubject().isBlank();
            
            return hasPublicKey && hasPrivateKey && hasSubject;
            
        } catch (Exception e) {
            log.error("Web Push provider health check failed", e);
            return false;
        }
    }

    @Override
    public int getPriority() {
        return pushProperties.getWeb().getPriority();
    }
}