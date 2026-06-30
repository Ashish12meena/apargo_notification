package com.aigreentick.services.notification.dto.request.email;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTemplatedEmailRequest {
    
    @NotBlank(message = "Template code is required")
    private String templateCode;
    
    @NotEmpty(message = "At least one recipient is required")
    private List<@Email String> to;
    
    private List<@Email String> cc;
    private List<@Email String> bcc;
    
    @NotEmpty(message = "Template variables are required")
    private Map<String, Object> variables;
    
    private List<EmailAttachment> attachments;
}