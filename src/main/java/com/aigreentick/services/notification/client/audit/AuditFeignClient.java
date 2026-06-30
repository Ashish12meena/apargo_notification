package com.aigreentick.services.notification.client.audit;

import com.aigreentick.services.notification.client.audit.config.AuditClientConfig;
import com.aigreentick.services.notification.client.audit.dto.AuditRequest;
import com.aigreentick.services.notification.client.audit.dto.AuditResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for Audit Service
 * 
 * No fallback needed - if audit fails, we just log it
 * No circuit breaker needed - fire and forget
 */
@FeignClient(
    name = "audit-service",
    url = "${audit.service.url}",
    configuration = AuditClientConfig.class
)
public interface AuditFeignClient {
    
    /**
     * Send audit data to audit service
     * 
     * @param request Audit request data
     * @return Audit response (typically ignored)
     */
    @PostMapping("/api/v1/audit")
    AuditResponse sendAudit(@RequestBody AuditRequest request);
}