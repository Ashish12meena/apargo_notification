package com.aigreentick.services.notification.service.email.impl;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;
import com.aigreentick.services.notification.exceptions.EmailTemplateNotFoundException;
import com.aigreentick.services.notification.exceptions.EmailTemplateProcessingException;
import com.aigreentick.services.notification.model.entity.EmailTemplate;
import com.aigreentick.services.notification.repository.EmailTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateProcessorService {

    private final EmailTemplateRepository templateRepository;
    
    @Qualifier("stringTemplateEngine")
    private final TemplateEngine stringTemplateEngine;
    
    @Qualifier("fileTemplateEngine") 
    private final TemplateEngine fileTemplateEngine;

    /**
     * Get template by code with Redis caching
     * Spring Cache will automatically handle Redis storage
     */
    @Cacheable(value = "emailTemplates", key = "#templateCode", unless = "#result == null")
    public EmailTemplate getTemplateByCode(String templateCode) {
        log.debug("Fetching template from database: {}", templateCode);
        
        return templateRepository.findByTemplateCode(templateCode)
                .orElseThrow(() -> new EmailTemplateNotFoundException(
                        "Template not found: " + templateCode));
    }

    /**
     * Process template with variables
     */
    public EmailNotificationRequest processTemplateByCode(
            String templateCode,
            Map<String, Object> variables,
            EmailNotificationRequest baseRequest) {
        
        log.info("Processing template by code: {}", templateCode);

        // Cache will be used here
        EmailTemplate template = getTemplateByCode(templateCode);

        if (!template.isActive()) {
            throw new EmailTemplateProcessingException(
                    "Template is inactive: " + templateCode);
        }

        Context context = new Context();
        context.setVariables(variables);

        String processedSubject = stringTemplateEngine.process(
                template.getSubject(), context);

        String processedBody = stringTemplateEngine.process(
                template.getBody(), context);

        return baseRequest.toBuilder()
                .subject(processedSubject)
                .body(processedBody)
                .isHtml(true)
                .build();
    }

    /**
     * Process file-based template
     */
    public String processFileTemplate(String templateName, Map<String, Object> variables) {
        log.info("Processing file template: {}", templateName);

        Context context = new Context();
        context.setVariables(variables);

        return fileTemplateEngine.process(templateName, context);
    }

    /**
     * Validate template syntax
     */
    public boolean validateTemplate(String templateContent) {
        try {
            Context context = new Context();
            stringTemplateEngine.process(templateContent, context);
            return true;
        } catch (Exception e) {
            log.error("Template validation failed", e);
            return false;
        }
    }

    /**
     * Evict template cache
     */
    @CacheEvict(value = "emailTemplates", key = "#templateCode")
    public void evictTemplateCache(String templateCode) {
        log.info("Evicting template cache for: {}", templateCode);
    }
    
    /**
     * Clear all template cache
     */
    @CacheEvict(value = "emailTemplates", allEntries = true)
    public void clearAllTemplateCache() {
        log.info("Clearing all template cache");
    }
}