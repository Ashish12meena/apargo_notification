package com.aigreentick.services.notification.exceptions;

public class EmailTemplateNotFoundException  extends RuntimeException{

    public EmailTemplateNotFoundException(String message) {
        super(message);
    }
    
}
