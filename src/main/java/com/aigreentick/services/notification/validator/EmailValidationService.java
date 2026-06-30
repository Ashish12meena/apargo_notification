package com.aigreentick.services.notification.validator;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.config.properties.EmailProperties;
import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailValidationService {
    
    private final EmailProperties emailProperties;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public void validateEmailRequest(EmailNotificationRequest request) {
        if (!emailProperties.getValidation().isEnabled()) {
            return;
        }
        
        log.debug("Validating email request for: {}", request.getTo());
        
        validateRecipients(request);
        validateAttachments(request);
        validateBodySize(request);
    }

    private void validateRecipients(EmailNotificationRequest request) {
        EmailProperties.ValidationProperties validation = emailProperties.getValidation();
        
        if (request.getTo() == null || request.getTo().isEmpty()) {
            throw new IllegalArgumentException("At least one recipient is required");
        }
        
        if (request.getTo().size() > validation.getMaxRecipients()) {
            throw new IllegalArgumentException(
                    "Maximum " + validation.getMaxRecipients() + " recipients allowed");
        }
        
        if (request.getCc() != null && 
            request.getCc().size() > validation.getMaxCcRecipients()) {
            throw new IllegalArgumentException(
                    "Maximum " + validation.getMaxCcRecipients() + " CC recipients allowed");
        }
        
        if (request.getBcc() != null && 
            request.getBcc().size() > validation.getMaxBccRecipients()) {
            throw new IllegalArgumentException(
                    "Maximum " + validation.getMaxBccRecipients() + " BCC recipients allowed");
        }
        
        request.getTo().forEach(this::validateEmailFormat);
        
        if (request.getCc() != null) {
            request.getCc().forEach(this::validateEmailFormat);
        }
        
        if (request.getBcc() != null) {
            request.getBcc().forEach(this::validateEmailFormat);
        }
    }

    private void validateEmailFormat(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
    }

    private void validateAttachments(EmailNotificationRequest request) {
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) {
            return;
        }
        
        EmailProperties.ValidationProperties validation = emailProperties.getValidation();
        EmailProperties.AttachmentProperties attachmentProps = emailProperties.getAttachments();
        
        if (request.getAttachments().size() > validation.getMaxAttachments()) {
            throw new IllegalArgumentException(
                    "Maximum " + validation.getMaxAttachments() + " attachments allowed");
        }
        
        long totalSize = 0;
        for (var attachment : request.getAttachments()) {
            long sizeInMb = attachment.getContent().length / (1024 * 1024);
            
            if (sizeInMb > attachmentProps.getMaxSizePerFileMb()) {
                throw new IllegalArgumentException(
                        "Attachment " + attachment.getFilename() + 
                        " exceeds maximum size of " + 
                        attachmentProps.getMaxSizePerFileMb() + "MB");
            }
            
            totalSize += attachment.getContent().length;
        }
        
        long totalSizeInMb = totalSize / (1024 * 1024);
        if (totalSizeInMb > attachmentProps.getMaxTotalSizeMb()) {
            throw new IllegalArgumentException(
                    "Total attachments size exceeds maximum of " + 
                    attachmentProps.getMaxTotalSizeMb() + "MB");
        }
    }

    private void validateBodySize(EmailNotificationRequest request) {
        if (request.getBody() == null) {
            return;
        }
        
        EmailProperties.ValidationProperties validation = emailProperties.getValidation();
        
        long sizeInKb = request.getBody().getBytes().length / 1024;
        if (sizeInKb > validation.getMaxBodySizeKb()) {
            throw new IllegalArgumentException(
                    "Email body exceeds maximum size of " + 
                    validation.getMaxBodySizeKb() + "KB");
        }
    }
}