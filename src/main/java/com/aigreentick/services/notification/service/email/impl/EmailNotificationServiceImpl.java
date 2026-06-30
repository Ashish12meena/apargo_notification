package com.aigreentick.services.notification.service.email.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.model.entity.EmailNotification;
import com.aigreentick.services.notification.repository.EmailNotificationRepository;
import com.aigreentick.services.notification.service.email.interfaces.MongoBaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationServiceImpl extends MongoBaseService<EmailNotification,String>{
    private final EmailNotificationRepository emailNotificationRepository;


    @Override
    protected EmailNotificationRepository getRepository() {
        return emailNotificationRepository;
    }
    
}
