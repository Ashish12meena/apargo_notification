package com.aigreentick.services.notification.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.notification.model.entity.EmailTemplate;

@Repository
public interface EmailTemplateRepository extends MongoRepository<EmailTemplate, String> {
    
    /**
     * Find template by unique template code
     */
    Optional<EmailTemplate> findByTemplateCode(String templateCode);
    
    /**
     * Check if template code exists
     */
    boolean existsByTemplateCode(String templateCode);
}