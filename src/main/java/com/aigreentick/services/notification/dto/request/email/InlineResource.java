package com.aigreentick.services.notification.dto.request.email;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inline resource DTO for HTML emails (e.g., embedded images)
 * Location: com.notification.dto.attachment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InlineResource {
    
    /**
     * Content ID used to reference in HTML (e.g., "logo" for <img src="cid:logo">)
     */
    @NotNull(message = "Content ID is required")
    private String contentId;

    /**
     * Binary content of the inline resource
     */
    @NotNull(message = "Content is required")
    private byte[] content;

    /**
     * MIME type of the resource (e.g., "image/png", "image/jpeg")
     */
    @NotNull(message = "Content type is required")
    private String contentType;
}