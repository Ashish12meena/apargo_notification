package com.aigreentick.services.notification.provider.email;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.aigreentick.services.notification.config.properties.EmailProperties;
import com.aigreentick.services.notification.config.properties.SmtpProviderProperties;
import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;
import com.aigreentick.services.notification.enums.email.EmailProviderType;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vavr.control.Try;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SMTP Email Provider with Circuit Breaker protection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailProvider implements EmailProviderStrategy {
    
    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final SmtpProviderProperties smtpProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public void send(EmailNotificationRequest request) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("smtpProvider");
        
        Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            sendEmailInternal(request);
            return null;
        }))
        .onFailure(throwable -> {
            if (throwable instanceof CallNotPermittedException) {
                log.error("SMTP Circuit Breaker is OPEN. Request rejected for: {}", request.getTo());
            } else {
                log.error("SMTP send failed for: {}", request.getTo(), throwable);
            }
        })
        .getOrElseThrow(ex -> {
            if (ex instanceof RuntimeException) {
                return (RuntimeException) ex;
            }
            return new RuntimeException("SMTP send failed", ex);
        });
    }

    /**
     * Internal method to send email
     */
    private void sendEmailInternal(EmailNotificationRequest request) {
        try {
            MimeMessage message = buildMimeMessage(request);
            mailSender.send(message);
            log.info("Email sent successfully to: {}", request.getTo());
        } catch (MailException | MessagingException e) {
            log.error("Failed to send email to: {}", request.getTo(), e);
            throw new RuntimeException("SMTP send failed", e);
        }
    }

    @Override
    public EmailProviderType getProviderType() {
        return EmailProviderType.SMTP;
    }

    @Override
    public boolean isAvailable() {
        if (!smtpProperties.isEnabled()) {
            return false;
        }
        
        // Check circuit breaker state
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("smtpProvider");
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.warn("SMTP Circuit Breaker is OPEN - provider unavailable");
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking circuit breaker state", e);
        }
        
        try {
            return mailSender != null;
        } catch (Exception e) {
            log.error("SMTP provider health check failed", e);
            return false;
        }
    }

    @Override
    public int getPriority() {
        return smtpProperties.getPriority();
    }

    private MimeMessage buildMimeMessage(EmailNotificationRequest request) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, emailProperties.getEncoding());

        helper.setFrom(emailProperties.getFromEmail());
        helper.setTo(request.getTo().toArray(new String[0]));
        helper.setSubject(request.getSubject());
        helper.setText(request.getBody(), request.isHtml());

        // Add CC recipients
        if (!CollectionUtils.isEmpty(request.getCc())) {
            helper.setCc(request.getCc().toArray(new String[0]));
        }

        // Add BCC recipients
        if (!CollectionUtils.isEmpty(request.getBcc())) {
            helper.setBcc(request.getBcc().toArray(new String[0]));
        }

        // Add attachments
        if (!CollectionUtils.isEmpty(request.getAttachments())) {
            request.getAttachments().forEach(attachment -> {
                try {
                    helper.addAttachment(
                            attachment.getFilename(),
                            new ByteArrayResource(attachment.getContent()),
                            attachment.getContentType());
                } catch (MessagingException e) {
                    log.error("Failed to add attachment: {}", attachment.getFilename(), e);
                }
            });
        }

        if (!CollectionUtils.isEmpty(request.getInlineResources())) {
            request.getInlineResources().forEach(resource -> {
                try {
                    helper.addInline(
                            resource.getContentId(),
                            new ByteArrayResource(resource.getContent()),
                            resource.getContentType());
                } catch (MessagingException e) {
                    log.error("Failed to add inline resource: {}", resource.getContentId(), e);
                }
            });
        }

        if (request.getPriority() != null) {
            message.setHeader("X-Priority", String.valueOf(request.getPriority().getValue()));
        }

        return message;
    }
}