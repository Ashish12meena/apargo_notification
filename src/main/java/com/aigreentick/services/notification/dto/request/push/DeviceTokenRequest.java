package com.aigreentick.services.notification.dto.request.push;

import com.aigreentick.services.notification.enums.push.DevicePlatform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {
    
    private String userId;
    
    private String deviceToken;
    
    private DevicePlatform platform;
    
    private String deviceModel;
    
    private String osVersion;
    
    private String appVersion;
    
    private String language;
}