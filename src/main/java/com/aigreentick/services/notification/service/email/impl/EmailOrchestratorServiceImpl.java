package com.aigreentick.services.notification.service.email.impl;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.notification.dto.request.email.EmailNotificationControllerRequest;
import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;
import com.aigreentick.services.notification.dto.request.email.SendTemplatedEmailRequest;
import com.aigreentick.services.notification.dto.response.AsyncEmailResponse;
import com.aigreentick.services.notification.dto.response.EmailNotificationResponse;
import com.aigreentick.services.notification.enums.NotificationStatus;
import com.aigreentick.services.notification.exceptions.EmailTemplateNotFoundException;
import com.aigreentick.services.notification.mapper.EmailNotificationMapper;
import com.aigreentick.services.notification.model.entity.EmailNotification;
import com.aigreentick.services.notification.validator.EmailValidationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOrchestratorServiceImpl {

        private final EmailDeliveryServiceImpl emailDeliveryService;
        private final EmailTemplateProcessorService templateProcessor;
        private final EmailValidationService validationService;
        private final EmailNotificationMapper emailNotificationMapper;
        private final EmailNotificationServiceImpl notificationService;

        // ==================== SYNCHRONOUS Email Sending ====================

        /**
         * Send email synchronously - waits for completion
         */
        public EmailNotificationResponse sendEmail(
                        EmailNotificationControllerRequest request,
                        List<MultipartFile> attachmentFiles,
                        List<MultipartFile> inlineResources) {

                log.info("Orchestrating SYNC email send to: {}", request.getTo());

                EmailNotificationRequest emailRequest = emailNotificationMapper
                                .toEmailRequest(request, attachmentFiles, inlineResources);

                validationService.validateEmailRequest(emailRequest);

                EmailNotification notification = emailDeliveryService.deliver(emailRequest);

                return mapToResponse(notification);
        }

        // ==================== ASYNCHRONOUS Email Sending ====================

        /**
         * Send email asynchronously - returns immediately with tracking ID
         * 
         * Flow:
         * 1. Validate request
         * 2. Create notification record in PENDING status
         * 3. Submit to async processor
         * 4. Return notification ID immediately
         */
        public AsyncEmailResponse sendEmailAsync(
                        EmailNotificationControllerRequest request,
                        List<MultipartFile> attachmentFiles,
                        List<MultipartFile> inlineResources) {

                log.info("Orchestrating ASYNC email send to: {}", request.getTo());

                EmailNotificationRequest emailRequest = emailNotificationMapper
                                .toEmailRequest(request, attachmentFiles, inlineResources);

                validationService.validateEmailRequest(emailRequest);

                EmailNotification notification = emailDeliveryService.createPendingNotification(emailRequest);

                emailDeliveryService.deliverAsync(emailRequest, notification.getId());

                return AsyncEmailResponse.builder()
                                .notificationId(notification.getId())
                                .status(NotificationStatus.PENDING)
                                .message("Email accepted for processing")
                                .acceptedAt(Instant.now())
                                .estimatedProcessingTimeSeconds(5)
                                .statusCheckUrl("/api/v1/notification/email/status/" + notification.getId())
                                .build();
        }

        // ==================== TEMPLATED Email Sending ====================

        /**
         * Send templated email synchronously
         */
        public EmailNotificationResponse sendTemplatedEmail(SendTemplatedEmailRequest request) {
                log.info("Orchestrating SYNC templated email send to: {} with template: {}",
                                request.getTo(), request.getTemplateCode());

                EmailNotificationRequest processedRequest = processTemplate(request);
                validationService.validateEmailRequest(processedRequest);

                EmailNotification notification = emailDeliveryService.deliver(processedRequest);
                return mapToResponse(notification);
        }

        /**
         * Send templated email asynchronously
         */
        public AsyncEmailResponse sendTemplatedEmailAsync(SendTemplatedEmailRequest request) {
                log.info("Orchestrating ASYNC templated email send to: {} with template: {}",
                                request.getTo(), request.getTemplateCode());

                EmailNotificationRequest processedRequest = processTemplate(request);
                validationService.validateEmailRequest(processedRequest);

                // Create notification record in PENDING status
                EmailNotification notification = emailDeliveryService.createPendingNotification(processedRequest);

                // Submit for async processing
                emailDeliveryService.deliverAsync(processedRequest, notification.getId());

                return AsyncEmailResponse.builder()
                                .notificationId(notification.getId())
                                .status(NotificationStatus.PENDING)
                                .message("Templated email accepted for processing")
                                .acceptedAt(Instant.now())
                                .estimatedProcessingTimeSeconds(5)
                                .statusCheckUrl("/api/v1/notification/email/status/" + notification.getId())
                                .build();
        }

        // ==================== BATCH Email Sending ====================

        /**
         * Send batch of emails asynchronously
         * 
         * @param inlineResources
         * @param attachmentFiles
         */
        public List<AsyncEmailResponse> sendBatchEmailAsync(
                        List<EmailNotificationControllerRequest> requests,
                        List<MultipartFile> attachmentFiles,
                        List<MultipartFile> inlineResources) {

                log.info("Orchestrating ASYNC batch email send for {} emails", requests.size());

                return requests.stream()
                                .map(request -> sendEmailAsync(request, attachmentFiles, inlineResources))
                                .collect(Collectors.toList());
        }

        // ==================== STATUS CHECKING ====================

        /**
         * Get status of an email notification
         */
        public EmailNotificationResponse getEmailStatus(String notificationId) {
                log.debug("Fetching status for notification: {}", notificationId);

                EmailNotification notification = notificationService.findOptionalById(notificationId)
                                .orElseThrow(() -> new EmailTemplateNotFoundException(
                                                "Notification not found: " + notificationId));

                return mapToResponse(notification);
        }

        // ==================== HELPER METHODS ====================

        private EmailNotificationRequest processTemplate(SendTemplatedEmailRequest request) {
                EmailNotificationRequest baseRequest = EmailNotificationRequest.builder()
                                .to(request.getTo())
                                .cc(request.getCc())
                                .bcc(request.getBcc())
                                .attachments(request.getAttachments())
                                .build();

                return templateProcessor.processTemplateByCode(
                                request.getTemplateCode(),
                                request.getVariables(),
                                baseRequest);
        }

        private EmailNotificationResponse mapToResponse(EmailNotification notification) {
                return EmailNotificationResponse.builder()
                                .id(notification.getId())
                                .to(notification.getTo())
                                .subject(notification.getSubject())
                                .status(notification.getStatus())
                                .providerType(notification.getProviderType())
                                .retryCount(notification.getRetryCount())
                                .createdAt(notification.getCreatedAt())
                                .updatedAt(notification.getUpdatedAt())
                                .build();
        }
}