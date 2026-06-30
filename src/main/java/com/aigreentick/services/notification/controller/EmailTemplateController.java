package com.aigreentick.services.notification.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.notification.dto.request.email.CreateTemplateRequest;
import com.aigreentick.services.notification.dto.request.email.TemplateResponse;
import com.aigreentick.services.notification.service.email.impl.EmailTemplateService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("api/v1/notifications/email/templates")
@RequiredArgsConstructor
public class EmailTemplateController {
    private final EmailTemplateService templateService;

    @PostMapping
    public ResponseEntity<TemplateResponse> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        log.info("Received request to create template: {}", request.getTemplateCode());

        TemplateResponse response = templateService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemplateResponse> updateTemplate(
            @PathVariable String id,
            @Valid @RequestBody CreateTemplateRequest request) {
        log.info("Received request to update template: {}", id);

        TemplateResponse response = templateService.updateTemplate(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplateById(@PathVariable String id) {
        log.info("Received request to get template by ID: {}", id);

        TemplateResponse response = templateService.getTemplateById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<TemplateResponse> getTemplateByCode(@PathVariable String code) {
        log.info("Received request to get template by code: {}", code);

        TemplateResponse response = templateService.getTemplateByCode(code);
        return ResponseEntity.ok(response);
    }

     @GetMapping
    public ResponseEntity<List<TemplateResponse>> getAllTemplates() {
        log.info("Received request to get all templates");
        
        List<TemplateResponse> response = templateService.getAllTemplates();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        log.info("Received request to delete template: {}", id);
        
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<TemplateResponse> activateTemplate(@PathVariable String id) {
        log.info("Received request to activate template: {}", id);
        
        TemplateResponse response = templateService.activateTemplate(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<TemplateResponse> deactivateTemplate(@PathVariable String id) {
        log.info("Received request to deactivate template: {}", id);
        
        TemplateResponse response = templateService.deactivateTemplate(id);
        return ResponseEntity.ok(response);
    }
}
