package com.aigreentick.services.notification.dto.request.email;

import java.util.List;

import com.aigreentick.services.notification.enums.email.EmailPriority;

import lombok.Data;

@Data
public class EmailNotificationControllerRequest {
    private List<String> to;

    private List<String> cc;

    private List<String> bcc;

    private String subject;

    private String body;

    private boolean isHtml;

    private EmailPriority priority;

    private List<String> inlineResourceIds;
}
