package com.aigreentick.services.notification.dto.request.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.aigreentick.services.notification.enums.email.EmailPriority;

/**
 * Request DTO for sending emails with full feature support
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationRequest {

    private List<String> to;

    private List<String> cc;

    private List<String> bcc;

    private String subject;

    private String body;

    private boolean isHtml;

    private EmailPriority priority;

    private List<EmailAttachment> attachments;

    private List<InlineResource> inlineResources;

}
