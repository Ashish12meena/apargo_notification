package com.aigreentick.services.notification.exceptions;

public class DeviceTokenNotFoundException extends RuntimeException {
    
    public DeviceTokenNotFoundException(String message) {
        super(message);
    }
    
    public DeviceTokenNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}