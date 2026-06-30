package com.aigreentick.services.notification.mapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.notification.dto.request.email.EmailAttachment;
import com.aigreentick.services.notification.dto.request.email.EmailNotificationControllerRequest;
import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;
import com.aigreentick.services.notification.dto.request.email.InlineResource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EmailNotificationMapper {
    /**
     * Converts multipart request + files to internal EmailNotificationRequest
     */
    public EmailNotificationRequest toEmailRequest(
            EmailNotificationControllerRequest multipartRequest,
            List<MultipartFile> attachmentFiles,
            List<MultipartFile> inlineResourceFiles) {
        log.debug("Mapping multipart request to EmailNotificationRequest");

        List<EmailAttachment> attachments = convertMultipartFiles(attachmentFiles);
        List<InlineResource> inlineResources = convertAndValidateInlineResources(
                inlineResourceFiles,
                multipartRequest.getInlineResourceIds());

        return EmailNotificationRequest.builder()
                .to(multipartRequest.getTo())
                .cc(multipartRequest.getCc())
                .bcc(multipartRequest.getBcc())
                .subject(multipartRequest.getSubject())
                .body(multipartRequest.getBody())
                .isHtml(multipartRequest.isHtml())
                .priority(multipartRequest.getPriority())
                .attachments(attachments)
                .inlineResources(inlineResources)
                .build();
    }

    public List<EmailAttachment> convertMultipartFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            log.debug("No attachments to convert");
            return Collections.emptyList();
        }

        log.info("Converting {} multipart file(s) to attachments", files.size());

        return files.stream()
                .filter(file -> !file.isEmpty())
                .map(this::convertSingleFile)
                .collect(Collectors.toList());
    }

    /**
     * Converts MultipartFiles to InlineResource list with content IDs
     */
    public List<InlineResource> convertAndValidateInlineResources(
            List<MultipartFile> inlineResourceFiles,
            List<String> contentIds) {

        if (inlineResourceFiles == null || inlineResourceFiles.isEmpty()) {
            log.debug("No inline resources to convert");
            return Collections.emptyList();
        }

        // Validate that contentIds match files count
        if (contentIds == null || contentIds.isEmpty()) {
            log.error("Inline resource files provided but no content IDs specified");
            throw new IllegalArgumentException(
                    "Content IDs must be provided for inline resources");
        }

        if (inlineResourceFiles.size() != contentIds.size()) {
            log.error("Mismatch: {} files but {} content IDs", inlineResourceFiles.size(), contentIds.size());
            throw new IllegalArgumentException(
                    String.format("Number of inline resource files (%d) must match number of content IDs (%d)",
                            inlineResourceFiles.size(), contentIds.size()));
        }

        log.info("Converting {} multipart file(s) to inline resources", inlineResourceFiles.size());

        // Map files with their corresponding content IDs
        return IntStream.range(0, inlineResourceFiles.size())
                .filter(i -> isValidFile(inlineResourceFiles.get(i)))
                .mapToObj(i -> convertFileToInlineResource(inlineResourceFiles.get(i), contentIds.get(i)))
                .collect(Collectors.toList());
    }

    /**
     * Converts single MultipartFile to InlineResource with content ID
     */
    private InlineResource convertFileToInlineResource(MultipartFile file, String contentId) {
        try {
            // Validate content ID
            if (contentId == null || contentId.isBlank()) {
                throw new IllegalArgumentException(
                        "Content ID cannot be null or empty for file: " + file.getOriginalFilename());
            }

            log.debug("Converting inline resource: {} with contentId: {} ({} bytes, type: {})",
                    file.getOriginalFilename(),
                    contentId,
                    file.getSize(),
                    file.getContentType());

            return InlineResource.builder()
                    .contentId(contentId.trim())
                    .content(file.getBytes())
                    .contentType(determineContentType(file))
                    .build();

        } catch (IOException e) {
            log.error("Failed to read inline resource file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to process inline resource: " +
                    file.getOriginalFilename(), e);
        }
    }


    /**
     * Determines content type with fallback
     */
    private String determineContentType(MultipartFile file) {
        String contentType = file.getContentType();

        if (contentType == null || contentType.isBlank()) {
            log.warn("No content type for file: {}, using default", file.getOriginalFilename());
            return "application/octet-stream";
        }

        return contentType;
    }

    /**
     * Converts single MultipartFile to EmailAttachment
     * Throws RuntimeException if file cannot be read
     */
    private EmailAttachment convertSingleFile(MultipartFile file) {
        try {
            log.debug("Converting file: {} ({} bytes, type: {})",
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType());

            return EmailAttachment.builder()
                    .filename(file.getOriginalFilename())
                    .content(file.getBytes())
                    .contentType(file.getContentType() != null
                            ? file.getContentType()
                            : "application/octet-stream")
                    .build();

        } catch (IOException e) {
            log.error("Failed to read multipart file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to process attachment: " +
                    file.getOriginalFilename(), e);
        }
    }

    /**
     * Validates that a file can be safely processed
     */
    public boolean isValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            log.warn("File has no filename");
            return false;
        }

        return true;
    }
}
