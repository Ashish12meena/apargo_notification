package com.aigreentick.services.notification.exceptions;


public class NotificationSendException extends RuntimeException {
    public NotificationSendException(String message) {
        super(message);
    }
    
    public NotificationSendException(String message, Throwable cause) {
        super(message, cause);
    }
}