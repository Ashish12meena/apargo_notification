package com.aigreentick.services.notification.provider.push;

import com.aigreentick.services.notification.dto.request.push.PushNotificationRequest;
import com.aigreentick.services.notification.enums.NotificationChannel;
import com.aigreentick.services.notification.enums.push.PushProviderType;

/**
 * Specific interface for Push notification providers (FCM, APNs)
 * Extends the generic NotificationProvider with Push-specific type
 */
public interface PushProviderStrategy  {
    
   void send(PushNotificationRequest request);
    
    PushProviderType getProviderType();
    
    boolean isAvailable();
    
    int getPriority();
    
    default NotificationChannel getChannel() {
        return NotificationChannel.PUSH;
    }
    
}

