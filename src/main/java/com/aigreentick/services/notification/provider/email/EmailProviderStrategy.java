package com.aigreentick.services.notification.provider.email;

import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;
import com.aigreentick.services.notification.enums.NotificationChannel;
import com.aigreentick.services.notification.enums.email.EmailProviderType;

public interface EmailProviderStrategy {
    void send(EmailNotificationRequest request);

    EmailProviderType getProviderType(); 

    boolean isAvailable();

    int getPriority();

    default NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }
}
