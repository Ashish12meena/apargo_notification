package com.aigreentick.services.notification.interceptor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.aigreentick.services.notification.service.ratelimit.InternalServiceRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Interceptor for internal service rate limiting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceRateLimitInterceptor implements HandlerInterceptor {

    private final InternalServiceRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        
        // Only apply to notification endpoints
        if (!request.getRequestURI().contains("/api/v1/notification")) {
            return true;
        }

        // Extract service identifier from header
        String serviceId = request.getHeader("X-Service-Id");
        if (serviceId == null || serviceId.isBlank()) {
            serviceId = "unknown-service";
            log.warn("No X-Service-Id header provided. URI: {}", request.getRequestURI());
        }

        
        log.debug("Rate limit check - serviceId: {}, URI: {}", 
                serviceId, request.getRequestURI());

        boolean allowed = rateLimiter.allowNotification(serviceId);

        if (!allowed) {
            log.warn("Rate limit exceeded - serviceId: {}, URI: {}", 
                    serviceId, request.getRequestURI());
            
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            
            RateLimitErrorResponse errorResponse = RateLimitErrorResponse.builder()
                    .error("Rate limit exceeded")
                    .message("Service has exceeded notification quota. Please try again later.")
                    .code("RATE_LIMIT_EXCEEDED")
                    .serviceId(serviceId)
                    .build();
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return false;
        }

        // Add rate limit headers
        addRateLimitHeaders(response, serviceId);

        return true;
    }

    private void addRateLimitHeaders(HttpServletResponse response, String serviceId) {
        try {
            long remainingGlobal = rateLimiter.getRemainingGlobal();
            long remainingService = rateLimiter.getRemainingForService(serviceId);
            
            response.setHeader("X-RateLimit-Remaining-Global", String.valueOf(remainingGlobal));
            response.setHeader("X-RateLimit-Remaining-Service", String.valueOf(remainingService));
            response.setHeader("X-RateLimit-Service", serviceId);
            response.setHeader("X-RateLimit-Window", "60s");
            
        } catch (Exception e) {
            log.error("Error adding rate limit headers", e);
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class RateLimitErrorResponse {
        private String error;
        private String message;
        private String code;
        private String serviceId;
    }
}