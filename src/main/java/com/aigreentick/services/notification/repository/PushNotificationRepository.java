package com.aigreentick.services.notification.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.notification.model.entity.PushNotification;

@Repository
public interface PushNotificationRepository extends MongoRepository<PushNotification, String> {
}