package com.aigreentick.services.notification.dto.request.email;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequest {
    
    private String templateCode;
    
    private String name;
    
    private String subject;
    
    private String body;
    
    private List<String> variables;
}