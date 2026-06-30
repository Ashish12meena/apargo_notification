package com.aigreentick.services.notification.service.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.config.properties.RateLimitProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Rate limiter for internal services
 * Supports: Global, Per-Service, Per-Recipient, Per-Template limits
 */
@Slf4j
@Service
public class InternalServiceRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitProperties rateLimitProperties;
    
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:notification:";
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);
    
    // Lua script for atomic rate limiting check
    private static final String RATE_LIMIT_LUA_SCRIPT = 
        "local key = KEYS[1] " +
        "local limit = tonumber(ARGV[1]) " +
        "local window_ms = tonumber(ARGV[2]) " +
        "local current_time = tonumber(ARGV[3]) " +
        "local window_start = current_time - window_ms " +
        
        "redis.call('ZREMRANGEBYSCORE', key, 0, window_start) " +
        "local current_count = redis.call('ZCARD', key) " +
        
        "if current_count < limit then " +
        "  redis.call('ZADD', key, current_time, current_time) " +
        "  redis.call('EXPIRE', key, math.ceil(window_ms / 1000)) " +
        "  return {1, limit - current_count - 1} " +
        "else " +
        "  return {0, 0} " +
        "end";

    private final DefaultRedisScript<List> rateLimitScript;

    public InternalServiceRateLimiter(
            RedisTemplate<String, String> redisTemplate,
            RateLimitProperties rateLimitProperties) {
        
        this.redisTemplate = redisTemplate;
        this.rateLimitProperties = rateLimitProperties;
        
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptText(RATE_LIMIT_LUA_SCRIPT);
        this.rateLimitScript.setResultType(List.class);
        
        log.info("Internal Service Rate Limiter initialized with global limit: {}/min, " +
                "service limit: {}/min, recipient limit: {}/min",
                rateLimitProperties.getGlobal().getRequestsPerMinute(),
                rateLimitProperties.getPerService().getRequestsPerMinute());
    }

    /**
     * Check if notification can be sent
     * 
     * @param serviceId     Calling service identifier (e.g., "order-service")
     * @param recipientEmail Recipient email address
     * @param templateCode  Optional template code
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean allowNotification(String serviceId) {
        if (!rateLimitProperties.isEnabled()) {
            return true;
        }

        try {
            // Level 1: Global system protection (CRITICAL)
            if (!checkLimit("global", 
                    rateLimitProperties.getGlobal().getRequestsPerMinute())) {
                log.error("GLOBAL rate limit exceeded - system overload!");
                return false;
            }

            // Level 2: Per-service protection
            if (rateLimitProperties.getPerService().isEnabled() && serviceId != null) {
                if (!checkLimit("service:" + serviceId, 
                        rateLimitProperties.getPerService().getRequestsPerMinute())) {
                    log.warn("Service rate limit exceeded for: {}", serviceId);
                    return false;
                }
            }

            return true;
            
        } catch (Exception e) {
            log.error("Error checking rate limit", e);
            // Fail open - allow request if Redis is down
            return true;
        }
    }

    /**
     * Check rate limit using Lua script
     */
    private boolean checkLimit(String key, int maxRequests) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long currentTime = System.currentTimeMillis();
        long windowMs = WINDOW_DURATION.toMillis();

        try {
            List<String> keys = new ArrayList<>();
            keys.add(redisKey);

            Object[] args = new Object[] {
                String.valueOf(maxRequests),
                String.valueOf(windowMs),
                String.valueOf(currentTime)
            };

            @SuppressWarnings("unchecked")
            List<Number> result = (List<Number>) redisTemplate.execute(
                    rateLimitScript, 
                    keys, 
                    args);

            if (result != null && !result.isEmpty()) {
                return result.get(0).longValue() == 1L;
            }

            return false;

        } catch (Exception e) {
            log.error("Error executing rate limit Lua script for key: {}", redisKey, e);
            return true; // Fail open
        }
    }

    /**
     * Get remaining capacity for a service
     */
    public long getRemainingForService(String serviceId) {
        return getRemaining("service:" + serviceId, 
                rateLimitProperties.getPerService().getRequestsPerMinute());
    }

    /**
     * Get remaining capacity for global limit
     */
    public long getRemainingGlobal() {
        return getRemaining("global", 
                rateLimitProperties.getGlobal().getRequestsPerMinute());
    }

    private long getRemaining(String key, int maxRequests) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - WINDOW_DURATION.toMillis();
        
        try {
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);
            Long currentCount = redisTemplate.opsForZSet().zCard(redisKey);
            
            if (currentCount == null) {
                return maxRequests;
            }
            
            return Math.max(0, maxRequests - currentCount);
            
        } catch (Exception e) {
            log.error("Error getting remaining capacity for key: {}", redisKey, e);
            return maxRequests;
        }
    }

    /**
     * Reset rate limit for a service (admin/testing)
     */
    public void resetServiceLimit(String serviceId) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + "service:" + serviceId;
        redisTemplate.delete(redisKey);
        log.info("Reset rate limit for service: {}", serviceId);
    }


    /**
     * Get current count for debugging
     */
    public int getCurrentCount(String key) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - WINDOW_DURATION.toMillis();
        
        try {
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);
            Long count = redisTemplate.opsForZSet().zCard(redisKey);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("Error getting current count for key: {}", redisKey, e);
            return 0;
        }
    }
}