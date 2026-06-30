package com.aigreentick.services.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.notification.enums.push.DevicePlatform;
import com.aigreentick.services.notification.model.entity.DeviceToken;

@Repository
public interface DeviceTokenRepository extends MongoRepository<DeviceToken, String> {
    
    Optional<DeviceToken> findByDeviceToken(String deviceToken);
    
    List<DeviceToken> findByUserId(String userId);
    
    List<DeviceToken> findByUserIdAndActive(String userId, boolean active);
    
    List<DeviceToken> findByUserIdAndPlatformAndActive(String userId, DevicePlatform platform, boolean active);
    
    boolean existsByDeviceToken(String deviceToken);
    
    void deleteByDeviceToken(String deviceToken);
}