package com.aigreentick.services.notification.exceptions;

public class ProviderNotAvailableException extends RuntimeException {
    public ProviderNotAvailableException(String message) {
        super(message);
    }
    
    public ProviderNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
