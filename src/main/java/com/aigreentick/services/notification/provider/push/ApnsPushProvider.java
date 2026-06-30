package com.aigreentick.services.notification.provider.push;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.config.properties.PushProperties;
import com.aigreentick.services.notification.dto.request.push.PushNotificationRequest;
import com.aigreentick.services.notification.enums.push.PushProviderType;
import com.aigreentick.services.notification.exceptions.PushNotificationException;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.DeliveryPriority;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.PushType;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Apple Push Notification Service (APNs) Provider
 * Uses Pushy library for HTTP/2-based APNs communication
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "push.apns", name = "enabled", havingValue = "true")
public class ApnsPushProvider implements PushProviderStrategy {

    private final PushProperties pushProperties;
    private ApnsClient apnsClient;

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing APNs client...");

            validateApnsConfiguration();

            PushProperties.ApnsConfig config = pushProperties.getApns();

            File keyFile = new File(config.getKeyPath());
            if (!keyFile.exists()) {
                throw new PushNotificationException(
                        "APNs key file not found: " + config.getKeyPath());
            }

            ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(
                    keyFile,
                    config.getTeamId(),
                    config.getKeyId());


            ApnsClientBuilder builder = new ApnsClientBuilder()
                    .setApnsServer(config.isProduction()
                            ? ApnsClientBuilder.PRODUCTION_APNS_HOST
                            : ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                    .setSigningKey(signingKey);

            apnsClient = builder.build();

            log.info("APNs client initialized successfully for {} environment",
                    config.isProduction() ? "PRODUCTION" : "DEVELOPMENT");

        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to initialize APNs client", e);
            throw new PushNotificationException("APNs initialization failed", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (apnsClient != null) {
            log.info("Shutting down APNs client...");
            apnsClient.close().join();
            log.info("APNs client closed successfully");
        }
    }

    @Override
    public void send(PushNotificationRequest request) {
        if (apnsClient == null) {
            throw new PushNotificationException("APNs client not initialized");
        }

        log.info("Sending APNs push notification to token: {}...",
                request.getDeviceToken().substring(0, Math.min(10, request.getDeviceToken().length())));

        try {
            String payload = buildApnsPayload(request);

            PushProperties.ApnsConfig config = pushProperties.getApns();

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(
                    request.getDeviceToken(),
                    config.getBundleId(),
                    payload,
                    Instant.now().plusSeconds(config.getTimeout() / 1000),
                    determinePriority(request),
                    PushType.ALERT,
                    null,
                    null);

            PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> sendNotificationFuture = apnsClient
                    .sendNotification(pushNotification);

            PushNotificationResponse<SimpleApnsPushNotification> response = sendNotificationFuture.get();

            if (response.isAccepted()) {
                log.info("APNs notification accepted. APNs ID: {}", response.getApnsId());
            } else {
                String rejection = response.getRejectionReason().orElse("Unknown reason");
                log.error("APNs notification rejected: {}", rejection);

                handleApnsRejection(rejection, response);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("APNs send interrupted", e);
            throw new PushNotificationException("APNs send interrupted", e);
        } catch (ExecutionException e) {
            log.error("Failed to send APNs notification", e);
            throw new PushNotificationException("Failed to send APNs notification", e.getCause());
        } catch (Exception e) {
            log.error("Unexpected error sending APNs notification", e);
            throw new PushNotificationException("Failed to send APNs notification", e);
        }
    }

    private String buildApnsPayload(PushNotificationRequest request) {
        SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();

        payloadBuilder.setAlertTitle(request.getTitle());
        payloadBuilder.setAlertBody(request.getBody());

        if (request.getSound() != null && !request.getSound().isBlank()) {
            payloadBuilder.setSound(request.getSound());
        } else {
            payloadBuilder.setSound("default");
        }

        if (request.getBadge() != null) {
            payloadBuilder.setBadgeNumber(request.getBadge());
        }

        if (request.getClickAction() != null) {
            payloadBuilder.setCategoryName(request.getClickAction());
        }

        if (request.getData() != null && !request.getData().isEmpty()) {
            for (Map.Entry<String, String> entry : request.getData().entrySet()) {
                payloadBuilder.addCustomProperty(entry.getKey(), entry.getValue());
            }
        }

        // Image URL for rich notifications
        if (request.getImageUrl() != null) {
            payloadBuilder.addCustomProperty("image-url", request.getImageUrl());
            payloadBuilder.setContentAvailable(true);
            payloadBuilder.setMutableContent(true);
        }

        String payload = payloadBuilder.build();

        log.debug("Built APNs payload: {}", payload);

        return payload;
    }

    private DeliveryPriority determinePriority(PushNotificationRequest request) {
        if (request.getPriority() != null && request.getPriority() >= 10) {
            return DeliveryPriority.IMMEDIATE;
        }
        return DeliveryPriority.CONSERVE_POWER;
    }

    private void handleApnsRejection(String rejectionReason,
            PushNotificationResponse<SimpleApnsPushNotification> response) {

        switch (rejectionReason) {
            case "BadDeviceToken":
            case "Unregistered":
                throw new PushNotificationException(
                        "Invalid or unregistered device token");

            case "BadTopic":
                throw new PushNotificationException(
                        "Invalid topic (bundle ID mismatch)");

            case "DeviceTokenNotForTopic":
                throw new PushNotificationException(
                        "Device token not valid for this bundle ID");

            case "PayloadTooLarge":
                throw new PushNotificationException(
                        "Notification payload exceeds 4KB limit");

            case "BadCertificate":
            case "BadCertificateEnvironment":
                throw new PushNotificationException(
                        "Certificate issue: " + rejectionReason);

            case "ExpiredProviderToken":
                throw new PushNotificationException(
                        "Provider token expired, needs refresh");

            case "TooManyRequests":
                throw new PushNotificationException(
                        "Rate limit exceeded for this device token");

            default:
                throw new PushNotificationException(
                        "APNs rejection: " + rejectionReason);
        }
    }

    private void validateApnsConfiguration() {
        PushProperties.ApnsConfig config = pushProperties.getApns();

        if (config.getTeamId() == null || config.getTeamId().isBlank()) {
            throw new PushNotificationException("APNs Team ID not configured");
        }

        if (config.getKeyId() == null || config.getKeyId().isBlank()) {
            throw new PushNotificationException("APNs Key ID not configured");
        }

        if (config.getKeyPath() == null || config.getKeyPath().isBlank()) {
            throw new PushNotificationException("APNs Key Path not configured");
        }

        if (config.getBundleId() == null || config.getBundleId().isBlank()) {
            throw new PushNotificationException("APNs Bundle ID not configured");
        }
    }

    @Override
    public PushProviderType getProviderType() {
        return PushProviderType.APNS;
    }

    @Override
    public boolean isAvailable() {
        if (!pushProperties.getApns().isEnabled()) {
            return false;
        }

        if (apnsClient == null) {
            return false;
        }

        try {
            PushProperties.ApnsConfig config = pushProperties.getApns();

            boolean hasTeamId = config.getTeamId() != null && !config.getTeamId().isBlank();
            boolean hasKeyId = config.getKeyId() != null && !config.getKeyId().isBlank();
            boolean hasKeyPath = config.getKeyPath() != null && !config.getKeyPath().isBlank();
            boolean hasBundleId = config.getBundleId() != null && !config.getBundleId().isBlank();

            return hasTeamId && hasKeyId && hasKeyPath && hasBundleId;

        } catch (Exception e) {
            log.error("APNs provider health check failed", e);
            return false;
        }
    }

    @Override
    public int getPriority() {
        return pushProperties.getApns().getPriority();
    }
}