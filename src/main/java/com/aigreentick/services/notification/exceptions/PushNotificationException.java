package com.aigreentick.services.notification.exceptions;

public class PushNotificationException extends RuntimeException {
    public PushNotificationException(String message){
        super(message);
    }
    public PushNotificationException(String message,Exception e){
        super(message);
    }
    public PushNotificationException(String message, Throwable cause) {
        super(message);
    }
}
