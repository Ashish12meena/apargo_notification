package com.aigreentick.services.notification.dto.request.push;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendPushRequest {
    
    private String userId;
    
    private String deviceToken;
    
    private String title;
    
    private String body;
    
    private Map<String, String> data;
    
    private String imageUrl;
    
    private String sound;
    
    private Integer badge;
}