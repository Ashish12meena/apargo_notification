// src/main/java/com/aigreentick/services/notification/service/idempotency/IdempotencyService.java
package com.aigreentick.services.notification.service.idempotency;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Idempotency service using Redis for deduplication
 * Prevents duplicate email processing using eventId
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:email:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24); // Keep for 24 hours
    
    /**
     * Check if event has already been processed
     * @param eventId Unique event identifier
     * @return true if this is the first time seeing this eventId, false if duplicate
     */
    public boolean isFirstProcessing(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            log.warn("EventId is null or empty, treating as non-duplicate");
            return true;
        }
        
        String key = IDEMPOTENCY_KEY_PREFIX + eventId;
        
        try {
            // Try to set the key only if it doesn't exist (NX)
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key, "PROCESSING", IDEMPOTENCY_TTL);
            
            if (Boolean.TRUE.equals(success)) {
                log.debug("First processing for eventId: {}", eventId);
                return true;
            } else {
                log.warn("Duplicate eventId detected: {}. Skipping processing.", eventId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error checking idempotency for eventId: {}. Allowing processing to continue.", 
                    eventId, e);
            // Fail open - allow processing if Redis is down
            return true;
        }
    }
    
    /**
     * Mark event as successfully processed
     */
    public void markAsProcessed(String eventId, String notificationId) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        
        String key = IDEMPOTENCY_KEY_PREFIX + eventId;
        
        try {
            redisTemplate.opsForValue().set(
                    key, 
                    "COMPLETED:" + notificationId, 
                    IDEMPOTENCY_TTL);
            
            log.debug("Marked eventId {} as processed with notificationId: {}", 
                    eventId, notificationId);
            
        } catch (Exception e) {
            log.error("Error marking eventId as processed: {}", eventId, e);
        }
    }
    
    /**
     * Mark event as failed
     */
    public void markAsFailed(String eventId, String reason) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        
        String key = IDEMPOTENCY_KEY_PREFIX + eventId;
        
        try {
            redisTemplate.opsForValue().set(
                    key, 
                    "FAILED:" + reason, 
                    IDEMPOTENCY_TTL);
            
            log.debug("Marked eventId {} as failed: {}", eventId, reason);
            
        } catch (Exception e) {
            log.error("Error marking eventId as failed: {}", eventId, e);
        }
    }
    
    /**
     * Get processing status for an eventId
     */
    public String getProcessingStatus(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return null;
        }
        
        String key = IDEMPOTENCY_KEY_PREFIX + eventId;
        
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error getting processing status for eventId: {}", eventId, e);
            return null;
        }
    }
    
    /**
     * Remove idempotency record (for testing/admin purposes)
     */
    public void removeIdempotencyRecord(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        
        String key = IDEMPOTENCY_KEY_PREFIX + eventId;
        redisTemplate.delete(key);
        log.info("Removed idempotency record for eventId: {}", eventId);
    }
}