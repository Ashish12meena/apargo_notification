package com.aigreentick.services.notification.dto.request.email;


import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Email attachment details DTO
 * Location: com.notification.dto.attachment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachment {
    
    /**
     * Filename for the attachment
     */
    @NotNull(message = "Filename is required")
    private String filename;

    /**
     * Binary content of the attachment
     */
    @NotNull(message = "Content is required")
    private byte[] content;

    /**
     * MIME type of the attachment (e.g., "application/pdf", "image/png")
     */
    @NotNull(message = "Content type is required")
    @Builder.Default
    private String contentType = "application/octet-stream";
}