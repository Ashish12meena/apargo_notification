package com.aigreentick.services.notification.service.email.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.notification.dto.request.email.CreateTemplateRequest;
import com.aigreentick.services.notification.dto.request.email.TemplateResponse;
import com.aigreentick.services.notification.exceptions.EmailTemplateNotFoundException;
import com.aigreentick.services.notification.model.entity.EmailTemplate;
import com.aigreentick.services.notification.repository.EmailTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateService {
    
    private final EmailTemplateRepository templateRepository;

    @Transactional
    public TemplateResponse createTemplate(CreateTemplateRequest request) {
        log.info("Creating email template: {}", request.getTemplateCode());
        
        if (templateRepository.existsByTemplateCode(request.getTemplateCode())) {
            throw new IllegalArgumentException(
                    "Template code already exists: " + request.getTemplateCode());
        }
    
        
        EmailTemplate template = EmailTemplate.builder()
                .templateCode(request.getTemplateCode())
                .name(request.getName())
                .subject(request.getSubject())
                .body(request.getBody())
                .variables(request.getVariables())
                .active(true)
                .createdAt(Instant.now())
                .build();
        
        template = templateRepository.save(template);
        
        log.info("Template created successfully: {}", template.getId());
        return mapToResponse(template);
    }

    @Transactional
    public TemplateResponse updateTemplate(String id, CreateTemplateRequest request) {
        log.info("Updating email template: {}", id);
        
        EmailTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new EmailTemplateNotFoundException(
                        "Template not found: " + id));
        
    
        template.setName(request.getName());
        template.setSubject(request.getSubject());
        template.setBody(request.getBody());
        template.setVariables(request.getVariables());
        template.setUpdatedAt(Instant.now());
        
        template = templateRepository.save(template);
        
        log.info("Template updated successfully: {}", id);
        return mapToResponse(template);
    }

    @Transactional(readOnly = true)
    public TemplateResponse getTemplateById(String id) {
        EmailTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new EmailTemplateNotFoundException(
                        "Template not found: " + id));
        return mapToResponse(template);
    }

    @Transactional(readOnly = true)
    public TemplateResponse getTemplateByCode(String code) {
        EmailTemplate template = templateRepository.findByTemplateCode(code)
                .orElseThrow(() -> new EmailTemplateNotFoundException(
                        "Template not found: " + code));
        return mapToResponse(template);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> getAllTemplates() {
        return templateRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTemplate(String id) {
        log.info("Deleting email template: {}", id);
        
        if (!templateRepository.existsById(id)) {
            throw new EmailTemplateNotFoundException(
                    "Template not found: " + id);
        }
        
        templateRepository.deleteById(id);
        log.info("Template deleted successfully: {}", id);
    }

    @Transactional
    public TemplateResponse activateTemplate(String id) {
        EmailTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new EmailTemplateNotFoundException(
                        "Template not found: " + id));
        
        template.setActive(true);
        template.setUpdatedAt(Instant.now());
        template = templateRepository.save(template);
        
        log.info("Template activated: {}", id);
        return mapToResponse(template);
    }

    @Transactional
    public TemplateResponse deactivateTemplate(String id) {
        EmailTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new EmailTemplateNotFoundException(
                        "Template not found: " + id));
        
        template.setActive(false);
        template.setUpdatedAt(Instant.now());
        template = templateRepository.save(template);
        
        log.info("Template deactivated: {}", id);
        return mapToResponse(template);
    }

    private TemplateResponse mapToResponse(EmailTemplate template) {
        return TemplateResponse.builder()
                .id(template.getId())
                .templateCode(template.getTemplateCode())
                .name(template.getName())
                .subject(template.getSubject())
                .body(template.getBody())
                .variables(template.getVariables())
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}