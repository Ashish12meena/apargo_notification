package com.aigreentick.services.notification.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {
    
    private String id;
    private String templateCode;
    private String name;
    private String subject;
    private String body;
    private List<String> variables;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}