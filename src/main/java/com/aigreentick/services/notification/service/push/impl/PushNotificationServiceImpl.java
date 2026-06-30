package com.aigreentick.services.notification.service.push.impl;

import org.springframework.stereotype.Service;


import com.aigreentick.services.notification.model.entity.PushNotification;
import com.aigreentick.services.notification.repository.PushNotificationRepository;
import com.aigreentick.services.notification.service.email.interfaces.MongoBaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationServiceImpl extends MongoBaseService<PushNotification, String> {
    
    private final PushNotificationRepository pushNotificationRepository;
    
    @Override
    protected PushNotificationRepository getRepository() {
        return pushNotificationRepository;
    }
}