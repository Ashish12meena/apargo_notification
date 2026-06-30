package com.aigreentick.services.notification.service.push.impl;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.notification.dto.request.push.DeviceTokenRequest;
import com.aigreentick.services.notification.dto.response.push.DeviceTokenResponse;
import com.aigreentick.services.notification.exceptions.DeviceTokenNotFoundException;
import com.aigreentick.services.notification.model.entity.DeviceToken;
import com.aigreentick.services.notification.repository.DeviceTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {
    
    private final DeviceTokenRepository deviceTokenRepository;
    
    @Transactional
    public DeviceTokenResponse registerToken(DeviceTokenRequest request) {
        log.info("Registering device token for user: {}, platform: {}", 
                request.getUserId(), request.getPlatform());
        
        DeviceToken deviceToken = deviceTokenRepository.findByDeviceToken(request.getDeviceToken())
                .orElse(null);
        
        if (deviceToken != null) {
            log.info("Updating existing device token: {}", deviceToken.getId());
            updateExistingToken(deviceToken, request);
        } else {
            log.info("Creating new device token");
            deviceToken = createNewToken(request);
        }
        
        deviceToken = deviceTokenRepository.save(deviceToken);
        
        log.info("Device token registered successfully: {}", deviceToken.getId());
        return mapToResponse(deviceToken);
    }
    
    private void updateExistingToken(DeviceToken deviceToken, DeviceTokenRequest request) {
        deviceToken.setUserId(request.getUserId());
        deviceToken.setPlatform(request.getPlatform());
        deviceToken.setDeviceModel(request.getDeviceModel());
        deviceToken.setOsVersion(request.getOsVersion());
        deviceToken.setAppVersion(request.getAppVersion());
        deviceToken.setLanguage(request.getLanguage());
        deviceToken.setActive(true);
        deviceToken.setUpdatedAt(Instant.now());
    }
    
    private DeviceToken createNewToken(DeviceTokenRequest request) {
        return DeviceToken.builder()
                .userId(request.getUserId())
                .deviceToken(request.getDeviceToken())
                .platform(request.getPlatform())
                .deviceModel(request.getDeviceModel())
                .osVersion(request.getOsVersion())
                .appVersion(request.getAppVersion())
                .language(request.getLanguage())
                .active(true)
                .createdAt(Instant.now())
                .build();
    }
    
    @Transactional(readOnly = true)
    public List<DeviceTokenResponse> getUserTokens(String userId) {
        log.debug("Fetching device tokens for user: {}", userId);
        
        return deviceTokenRepository.findByUserIdAndActive(userId, true).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public DeviceToken getActiveTokenByValue(String tokenValue) {
        return deviceTokenRepository.findByDeviceToken(tokenValue)
                .filter(DeviceToken::isActive)
                .orElseThrow(() -> new DeviceTokenNotFoundException(
                        "Active device token not found: " + tokenValue));
    }
    
    @Transactional
    public void deactivateToken(String tokenValue) {
        log.info("Deactivating device token: {}", tokenValue);
        
        DeviceToken deviceToken = deviceTokenRepository.findByDeviceToken(tokenValue)
                .orElseThrow(() -> new DeviceTokenNotFoundException(
                        "Device token not found: " + tokenValue));
        
        deviceToken.setActive(false);
        deviceToken.setUpdatedAt(Instant.now());
        deviceTokenRepository.save(deviceToken);
        
        log.info("Device token deactivated successfully");
    }
    
    @Transactional
    public void deleteToken(String tokenValue) {
        log.info("Deleting device token: {}", tokenValue);
        deviceTokenRepository.deleteByDeviceToken(tokenValue);
    }
    
    private DeviceTokenResponse mapToResponse(DeviceToken deviceToken) {
        return DeviceTokenResponse.builder()
                .id(deviceToken.getId())
                .userId(deviceToken.getUserId())
                .deviceToken(deviceToken.getDeviceToken())
                .platform(deviceToken.getPlatform())
                .deviceModel(deviceToken.getDeviceModel())
                .osVersion(deviceToken.getOsVersion())
                .appVersion(deviceToken.getAppVersion())
                .active(deviceToken.isActive())
                .createdAt(deviceToken.getCreatedAt())
                .updatedAt(deviceToken.getUpdatedAt())
                .build();
    }
}