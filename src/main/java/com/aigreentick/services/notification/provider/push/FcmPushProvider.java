package com.aigreentick.services.notification.provider.push;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.config.properties.PushProperties;
import com.aigreentick.services.notification.dto.request.push.PushNotificationRequest;
import com.aigreentick.services.notification.enums.push.PushProviderType;
import com.aigreentick.services.notification.exceptions.PushNotificationException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
public class FcmPushProvider implements PushProviderStrategy {

    private final PushProperties pushProperties;
    private final FirebaseApp firebaseApp;

    @Override
    public void send(PushNotificationRequest request) {
        log.info("Sending FCM push notification to token: {}...",
                request.getDeviceToken().substring(0, 10));

        try {
            Message message = buildFcmMessage(request);

            String response = FirebaseMessaging.getInstance(firebaseApp)
                    .send(message, pushProperties.getFcm().isDryRun());

            log.info("Successfully sent FCM message. ID: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM notification: {}", e.getMessage(), e);
            handleFcmException(e);
        } catch (Exception e) {
            log.error("Unexpected error sending FCM notification", e);
            throw new PushNotificationException("Failed to send push notification", e);
        }
    }

    private Message buildFcmMessage(PushNotificationRequest request) {
        Message.Builder messageBuilder = Message.builder()
                .setToken(request.getDeviceToken())
                .setNotification(buildNotification(request))
                .setAndroidConfig(buildAndroidConfig(request))
                .setApnsConfig(buildApnsConfig(request));

        if (request.getData() != null && !request.getData().isEmpty()) {
            messageBuilder.putAllData(request.getData());
        }

        return messageBuilder.build();
    }

    private Notification buildNotification(PushNotificationRequest request) {
        Notification.Builder builder = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody());

        if (request.getImageUrl() != null) {
            builder.setImage(request.getImageUrl());
        }

        return builder.build();
    }

    private AndroidConfig buildAndroidConfig(PushNotificationRequest request) {
        AndroidNotification.Builder notificationBuilder = AndroidNotification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody());

        if (request.getSound() != null) {
            notificationBuilder.setSound(request.getSound());
        }

        if (request.getImageUrl() != null) {
            notificationBuilder.setImage(request.getImageUrl());
        }

        if (request.getClickAction() != null) {
            notificationBuilder.setClickAction(request.getClickAction());
        }

        AndroidConfig.Builder configBuilder = AndroidConfig.builder()
                .setNotification(notificationBuilder.build());

        if (request.getPriority() != null) {
            configBuilder.setPriority(request.getPriority() > 5
                    ? AndroidConfig.Priority.HIGH
                    : AndroidConfig.Priority.NORMAL);
        }

        if (request.getTtl() != null) {
            configBuilder.setTtl(request.getTtl() * 1000L);
        }

        return configBuilder.build();
    }

    private ApnsConfig buildApnsConfig(PushNotificationRequest request) {
        Aps.Builder apsBuilder = Aps.builder()
                .setAlert(ApsAlert.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getBody())
                        .build());

        if (request.getSound() != null) {
            apsBuilder.setSound(request.getSound());
        } else {
            apsBuilder.setSound("default");
        }

        if (request.getBadge() != null) {
            apsBuilder.setBadge(request.getBadge());
        }

        Map<String, String> apnsHeaders = new HashMap<>();
        if (request.getPriority() != null) {
            apnsHeaders.put("apns-priority", request.getPriority() > 5 ? "10" : "5");
        }

        ApnsConfig.Builder configBuilder = ApnsConfig.builder()
                .setAps(apsBuilder.build());

        if (!apnsHeaders.isEmpty()) {
            configBuilder.putAllHeaders(apnsHeaders);
        }

        return configBuilder.build();
    }

    private void handleFcmException(FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();

        switch (errorCode) {
            case INVALID_ARGUMENT:
            case UNREGISTERED:
                throw new PushNotificationException("Invalid device token: " + e.getMessage(), e);

            case SENDER_ID_MISMATCH:
                throw new PushNotificationException("Token belongs to different sender", e);

            case QUOTA_EXCEEDED:
                throw new PushNotificationException("FCM quota exceeded", e);

            case UNAVAILABLE:
            case INTERNAL:
                throw new PushNotificationException("FCM service temporarily unavailable", e);

            default:
                throw new PushNotificationException("FCM error: " + errorCode, e);
        }
    }

    @Override
    public PushProviderType getProviderType() {
        return PushProviderType.FCM;
    }

    @Override
    public boolean isAvailable() {
        if (!pushProperties.getFcm().isEnabled()) {
            return false;
        }

        try {
            return firebaseApp != null && FirebaseMessaging.getInstance(firebaseApp) != null;
        } catch (Exception e) {
            log.error("FCM provider health check failed", e);
            return false;
        }
    }

    @Override
    public int getPriority() {
        return pushProperties.getFcm().getPriority();
    }
}