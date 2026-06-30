package com.aigreentick.services.notification.validator;

import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.config.properties.PushProperties;
import com.aigreentick.services.notification.dto.request.push.SendPushRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushValidationService {
    
    private final PushProperties pushProperties;
    
    public void validateSendRequest(SendPushRequest request) {
        if (!pushProperties.getValidation().isEnabled()) {
            return;
        }
        
        log.debug("Validating push notification request");
        
        validateRecipient(request);
        validateContent(request);
        validateDataPayload(request);
    }
    
    private void validateRecipient(SendPushRequest request) {
        if (request.getDeviceToken() == null && request.getUserId() == null) {
            throw new IllegalArgumentException(
                    "Either device token or user ID must be provided");
        }
    }
    
    private void validateContent(SendPushRequest request) {
        PushProperties.ValidationConfig validation = pushProperties.getValidation();
        
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        
        if (request.getTitle().length() > validation.getMaxTitleLength()) {
            throw new IllegalArgumentException(
                    "Title exceeds maximum length of " + validation.getMaxTitleLength());
        }
        
        if (request.getBody() == null || request.getBody().isBlank()) {
            throw new IllegalArgumentException("Body is required");
        }
        
        if (request.getBody().length() > validation.getMaxBodyLength()) {
            throw new IllegalArgumentException(
                    "Body exceeds maximum length of " + validation.getMaxBodyLength());
        }
    }
    
    private void validateDataPayload(SendPushRequest request) {
        if (request.getData() == null || request.getData().isEmpty()) {
            return;
        }
        
        PushProperties.ValidationConfig validation = pushProperties.getValidation();
        
        String jsonData = request.getData().toString();
        long sizeInKb = jsonData.getBytes().length / 1024;
        
        if (sizeInKb > validation.getMaxDataPayloadKb()) {
            throw new IllegalArgumentException(
                    "Data payload exceeds maximum size of " + 
                    validation.getMaxDataPayloadKb() + "KB");
        }
    }
}