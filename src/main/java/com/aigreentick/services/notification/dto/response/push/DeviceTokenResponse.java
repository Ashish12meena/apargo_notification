package com.aigreentick.services.notification.dto.response.push;

import java.time.Instant;

import com.aigreentick.services.notification.enums.push.DevicePlatform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenResponse {
    
    private String id;
    private String userId;
    private String deviceToken;
    private DevicePlatform platform;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}