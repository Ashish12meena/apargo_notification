package com.aigreentick.services.notification.provider.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.config.properties.SendGridProviderProperties;
import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;
import com.aigreentick.services.notification.enums.email.EmailProviderType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "email.provider.sendgrid", name = "enabled", havingValue = "true")
public class SendGridEmailProvider implements EmailProviderStrategy {
    
    private final SendGridProviderProperties sendGridProperties;

    @Override
    public void send(EmailNotificationRequest request) {
        
        log.info("SendGrid provider called for: {}", request.getTo());
        throw new UnsupportedOperationException("SendGrid provider not yet implemented");
    }

    @Override
    public EmailProviderType getProviderType() {
        return EmailProviderType.SENDGRID;
    }

    @Override
    public boolean isAvailable() {
        return sendGridProperties.isEnabled() && 
               sendGridProperties.getApiKey() != null && 
               !sendGridProperties.getApiKey().isEmpty();
    }

    @Override
    public int getPriority() {
        return sendGridProperties.getPriority();
    }
}