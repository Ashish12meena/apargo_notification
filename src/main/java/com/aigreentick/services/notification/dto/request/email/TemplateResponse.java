package com.aigreentick.services.notification.dto.request.email;

import java.time.Instant;
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
    private boolean active;
    private List<String> variables;
    private Instant createdAt;
    private Instant updatedAt;
    
}
