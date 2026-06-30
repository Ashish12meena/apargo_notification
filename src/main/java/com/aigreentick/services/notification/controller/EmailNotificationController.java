package com.aigreentick.services.notification.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.notification.dto.request.email.EmailNotificationControllerRequest;
import com.aigreentick.services.notification.dto.request.email.SendTemplatedEmailRequest;
import com.aigreentick.services.notification.dto.response.AsyncEmailResponse;
import com.aigreentick.services.notification.dto.response.EmailNotificationResponse;
import com.aigreentick.services.notification.service.email.impl.EmailOrchestratorServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification/email")
public class EmailNotificationController {
    
    private final EmailOrchestratorServiceImpl emailOrchestratorService;

    /**
     * SYNCHRONOUS: Send email and wait for completion
     * Use case: Critical emails where you need immediate confirmation
     */
    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmailNotificationResponse> sendEmail(
            @RequestPart("request") @Valid EmailNotificationControllerRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachmentFiles,
            @RequestPart(value = "inline", required = false) List<MultipartFile> inlineResources) {
        
        log.info("Received SYNC request to send email to: {}", request.getTo());

        EmailNotificationResponse response = emailOrchestratorService.sendEmail(
                request, attachmentFiles, inlineResources);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ASYNCHRONOUS: Accept email request and return immediately with tracking ID
     * Use case: Bulk emails, non-critical notifications where immediate response not needed
     * 
     * Returns 202 Accepted with notification ID for status tracking
     */
    @PostMapping(value = "/send/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AsyncEmailResponse> sendEmailAsync(
            @RequestPart("request") @Valid EmailNotificationControllerRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachmentFiles,
            @RequestPart(value = "inline", required = false) List<MultipartFile> inlineResources) {
        
        log.info("Received ASYNC request to send email to: {}", request.getTo());

        AsyncEmailResponse response = emailOrchestratorService.sendEmailAsync(
                request, attachmentFiles, inlineResources);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * SYNCHRONOUS TEMPLATED: Send templated email and wait for completion
     */
    @PostMapping("/send/templated")
    public ResponseEntity<EmailNotificationResponse> sendTemplatedEmail(
            @Valid @RequestBody SendTemplatedEmailRequest request) {
        
        log.info("Received SYNC request to send templated email to: {} with template: {}",
                request.getTo(), request.getTemplateCode());

        EmailNotificationResponse response = emailOrchestratorService.sendTemplatedEmail(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ASYNCHRONOUS TEMPLATED: Accept templated email request and return immediately
     */
    @PostMapping("/send/templated/async")
    public ResponseEntity<AsyncEmailResponse> sendTemplatedEmailAsync(
            @Valid @RequestBody SendTemplatedEmailRequest request) {
        
        log.info("Received ASYNC request to send templated email to: {} with template: {}",
                request.getTo(), request.getTemplateCode());

        AsyncEmailResponse response = emailOrchestratorService.sendTemplatedEmailAsync(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Check status of async email delivery
     * 
     * @param notificationId The ID returned from async send endpoint
     */
    @GetMapping("/status/{notificationId}")
    public ResponseEntity<EmailNotificationResponse> getEmailStatus(
            @PathVariable String notificationId) {
        
        log.info("Checking status for notification: {}", notificationId);

        EmailNotificationResponse response = emailOrchestratorService.getEmailStatus(notificationId);
        
        return ResponseEntity.ok(response);
    }

      /**
     * Batch async send - accepts multiple emails with attachments
     * Returns list of notification IDs for tracking
     * 
     * Note: For batch with attachments, use multipart form data
     */
    // Currently not use ready
    @PostMapping(value = "/send/batch/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AsyncEmailResponse>> sendBatchEmailAsync(
            @RequestPart("requests") @Valid List<EmailNotificationControllerRequest> requests,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachmentFiles,
            @RequestPart(value = "inline", required = false) List<MultipartFile> inlineResources) {
        
        log.info("Received ASYNC batch request for {} emails with {} attachments", 
                requests.size(), 
                attachmentFiles != null ? attachmentFiles.size() : 0);

        List<AsyncEmailResponse> responses = emailOrchestratorService.sendBatchEmailAsync(
                requests, attachmentFiles, inlineResources);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(responses);
    }
}