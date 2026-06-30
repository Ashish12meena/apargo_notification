package com.aigreentick.services.notification.exceptions;

public class EmailTemplateProcessingException extends RuntimeException {

    public EmailTemplateProcessingException(String message, Exception e) {
        super(message);
    }

    public EmailTemplateProcessingException(String message) {
        super(message);
    }
    
}
