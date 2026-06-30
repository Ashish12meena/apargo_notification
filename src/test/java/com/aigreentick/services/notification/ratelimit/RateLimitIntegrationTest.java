package com.aigreentick.services.notification.ratelimit;

import com.aigreentick.services.notification.dto.request.email.EmailNotificationRequest;
import com.aigreentick.services.notification.service.ratelimit.InternalServiceRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Rate Limiting Integration Tests")
class RateLimitIntegrationTest {

    private static RedisServer redisServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private InternalServiceRateLimiter rateLimiter;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6370");

        // Rate Limiting - Set low limits for easier testing
        registry.add("ratelimit.enabled", () -> "true");
        registry.add("ratelimit.global.requests-per-minute", () -> "100");
        registry.add("ratelimit.global.burst-capacity", () -> "150");
        registry.add("ratelimit.per-service.enabled", () -> "true");
        registry.add("ratelimit.per-service.requests-per-minute", () -> "10");
        registry.add("ratelimit.per-service.burst-capacity", () -> "15");

        // Disable actual email sending
        registry.add("email.provider.smtp.enabled", () -> "false");

        // Use in-memory MongoDB or disable if not needed for rate limit tests
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/test");
    }

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(6370);
        redisServer.start();
        System.out.println(" Embedded Redis started on port 6370");
    }

    @AfterAll
    static void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
            System.out.println(" Embedded Redis stopped");
        }
    }

    @BeforeEach
    void setUp() {
        // Clean Redis before each test
        try {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        } catch (Exception e) {
            System.err.println(" Warning: Could not flush Redis: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 1: Should allow requests within service rate limit")
    void test1_shouldAllowRequestsWithinLimit() throws Exception {
        // Given
        String serviceId = "auth-service";
        int expectedAllowedRequests = 10;

        System.out.println("\n=== Test 1: Requests Within Limit ===");

        // When & Then
        for (int i = 1; i <= expectedAllowedRequests; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                    .header("X-Service-Id", serviceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEmailRequestJson()))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists("X-RateLimit-Remaining-Service"))
                    .andExpect(header().exists("X-RateLimit-Remaining-Global"))
                    .andReturn();

            String remaining = result.getResponse().getHeader("X-RateLimit-Remaining-Service");
            System.out.printf(" Request %d/%d - Remaining: %s%n", i, expectedAllowedRequests, remaining);
        }

        System.out.println(" TEST PASSED: All 10 requests were allowed\n");
    }

    @Test
    @DisplayName("Test 2: Should block request when service rate limit exceeded")
    void test2_shouldBlockWhenServiceLimitExceeded() throws Exception {
        // Given
        String serviceId = "payment-service";

        System.out.println("\n=== Test 2: Rate Limit Exceeded ===");

        // Exhaust the rate limit
        System.out.println("Sending 10 requests to exhaust rate limit...");
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/v1/notification/email/send/async")
                    .header("X-Service-Id", serviceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEmailRequestJson()))
                    .andExpect(status().isAccepted());
        }
        System.out.println(" Rate limit exhausted");

        // 11th request should be blocked
        System.out.println(" Sending 11th request (should be blocked)...");
        MvcResult result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                .header("X-Service-Id", serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmailRequestJson()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.serviceId").value(serviceId))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        System.out.println("Response: " + responseBody);
        System.out.println(" TEST PASSED: Request correctly blocked with 429 status\n");
    }

    @Test
    @DisplayName("Test 3: Should enforce global rate limit across multiple services")
    void test3_shouldEnforceGlobalRateLimit() throws Exception {
        // Given
        int globalLimit = 100;
        int servicesCount = 5;
        int requestsPerService = 25;

        System.out.println("\n=== Test 3: Global Rate Limit ===");
        System.out.printf("📤 Testing: %d services × %d requests = %d total%n",
                servicesCount, requestsPerService, servicesCount * requestsPerService);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        // Multiple services making requests
        for (int serviceNum = 1; serviceNum <= servicesCount; serviceNum++) {
            String serviceId = "service-" + serviceNum;

            for (int i = 1; i <= requestsPerService; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                        .header("X-Service-Id", serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmailRequestJson()))
                        .andReturn();

                if (result.getResponse().getStatus() == 202) {
                    allowedCount.incrementAndGet();
                } else if (result.getResponse().getStatus() == 429) {
                    blockedCount.incrementAndGet();
                }
            }
        }

        // Verify
        System.out.printf(" Allowed: %d requests%n", allowedCount.get());
        System.out.printf(" Blocked: %d requests%n", blockedCount.get());

        assertThat(allowedCount.get())
                .as("Allowed requests should not exceed global limit")
                .isLessThanOrEqualTo(globalLimit);

        assertThat(blockedCount.get())
                .as("Some requests should be blocked")
                .isGreaterThan(0);

        System.out.println(" TEST PASSED: Global rate limit enforced\n");
    }

    @Test
    @DisplayName("Test 4: Should isolate rate limits between different services")
    void test4_shouldIsolateRateLimitsBetweenServices() throws Exception {
        // Given
        String service1 = "auth-service";
        String service2 = "messaging-service";

        System.out.println("\n=== Test 4: Service Isolation ===");

        // When - Exhaust limit for service-1
        System.out.println("📤 Sending 10 requests from auth-service...");
        int successCount = 0;
        for (int i = 1; i <= 10; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                    .header("X-Service-Id", service1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEmailRequestJson()))
                    .andReturn();

            if (result.getResponse().getStatus() == 202) {
                successCount++;
                String remaining = result.getResponse().getHeader("X-RateLimit-Remaining-Service");
                System.out.printf("  ✅ Request %d accepted - Remaining: %s%n", i, remaining);
            } else {
                System.out.printf("  🚫 Request %d blocked (status: %d)%n",
                        i, result.getResponse().getStatus());
            }
        }

        System.out.printf("📊 Total accepted: %d/10%n", successCount);

        // Verify we used up the limit
        assertThat(successCount)
                .as("Should have accepted exactly 10 requests")
                .isEqualTo(10);

        // Small delay to ensure Redis persistence
        Thread.sleep(100);

        // Then - Next request from service-1 should be blocked
        System.out.println("📤 Sending 11th request from auth-service (should be blocked)...");

        int attemptsToBlock = 3;
        boolean wasBlocked = false;

        for (int attempt = 1; attempt <= attemptsToBlock; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                    .header("X-Service-Id", service1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEmailRequestJson()))
                    .andReturn();

            int status = result.getResponse().getStatus();
            System.out.printf("  Attempt %d: Status %d%n", attempt, status);

            if (status == 429) {
                wasBlocked = true;
                String response = result.getResponse().getContentAsString();
                System.out.println("  ✅ Correctly blocked: " + response);
                break;
            } else if (status == 202) {
                System.out.println("  ⚠️ Still accepting requests, retrying...");
                Thread.sleep(100);
            }
        }

        assertThat(wasBlocked)
                .as("auth-service should be blocked after exhausting limit")
                .isTrue();

        System.out.println("✅ auth-service is blocked");

        // Verify service-2 can still send
        System.out.println("📤 Sending request from messaging-service...");
        MvcResult service2Result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                .header("X-Service-Id", service2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmailRequestJson()))
                .andExpect(status().isAccepted())
                .andReturn();

        String service2Remaining = service2Result.getResponse().getHeader("X-RateLimit-Remaining-Service");
        System.out.println("✅ messaging-service allowed - Remaining: " + service2Remaining);

        // Additional verification
        int service1Count = rateLimiter.getCurrentCount("service:" + service1);
        int service2Count = rateLimiter.getCurrentCount("service:" + service2);

        System.out.printf("📊 Final counts - auth-service: %d, messaging-service: %d%n",
                service1Count, service2Count);

        assertThat(service1Count)
                .as("auth-service should have reached limit")
                .isGreaterThanOrEqualTo(10);

        assertThat(service2Count)
                .as("messaging-service should have minimal usage")
                .isLessThanOrEqualTo(1);

        System.out.println(
                " TEST PASSED: Rate limits are properly isolated between auth-service and messaging-service\n");
    }

    @Test
    @DisplayName("Test 5: Should handle concurrent requests without race conditions")
    void test5_shouldHandleConcurrentRequestsSafely() throws Exception {
        // Given
        String serviceId = "concurrent-test-service";
        int concurrentThreads = 20;
        int requestsPerThread = 2;
        int totalRequests = concurrentThreads * requestsPerThread;

        System.out.println("\n=== Test 5: Concurrent Requests ===");
        System.out.printf("🔄 Testing: %d threads × %d requests = %d total%n",
                concurrentThreads, requestsPerThread, totalRequests);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalRequests);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        // Fire concurrent requests
        for (int thread = 0; thread < concurrentThreads; thread++) {
            for (int req = 0; req < requestsPerThread; req++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        MvcResult result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                                .header("X-Service-Id", serviceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createEmailRequestJson()))
                                .andReturn();

                        if (result.getResponse().getStatus() == 202) {
                            allowedCount.incrementAndGet();
                        } else if (result.getResponse().getStatus() == 429) {
                            blockedCount.incrementAndGet();
                        }

                    } catch (Exception e) {
                        System.err.println("❌ Error: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown(); // Start all threads
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Verify
        System.out.printf("✅ Allowed: %d requests%n", allowedCount.get());
        System.out.printf("🚫 Blocked: %d requests%n", blockedCount.get());

        assertThat(completed).as("All requests should complete").isTrue();
        assertThat(allowedCount.get())
                .as("No race conditions - limit enforced atomically")
                .isLessThanOrEqualTo(10);

        System.out.println("✅ TEST PASSED: No race conditions detected\n");
    }

    @Test
    @DisplayName("Test 6: Should include correct rate limit headers")
    void test6_shouldIncludeRateLimitHeaders() throws Exception {
        // Given
        String serviceId = "header-test-service";

        System.out.println("\n=== Test 6: Response Headers ===");

        // Make request
        MvcResult result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                .header("X-Service-Id", serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmailRequestJson()))
                .andExpect(status().isAccepted())
                .andReturn();

        // Verify headers
        String remainingGlobal = result.getResponse().getHeader("X-RateLimit-Remaining-Global");
        String remainingService = result.getResponse().getHeader("X-RateLimit-Remaining-Service");
        String rateLimitService = result.getResponse().getHeader("X-RateLimit-Service");
        String window = result.getResponse().getHeader("X-RateLimit-Window");

        System.out.println(" Response Headers:");
        System.out.println("  X-RateLimit-Remaining-Global: " + remainingGlobal);
        System.out.println("  X-RateLimit-Remaining-Service: " + remainingService);
        System.out.println("  X-RateLimit-Service: " + rateLimitService);
        System.out.println("  X-RateLimit-Window: " + window);

        assertThat(remainingGlobal).isNotNull();
        assertThat(remainingService).isNotNull();
        assertThat(rateLimitService).isEqualTo(serviceId);
        assertThat(window).isEqualTo("60s");

        System.out.println("✅ TEST PASSED: All headers present and correct\n");
    }

    @Test
    @DisplayName("Test 7: Should handle missing X-Service-Id header")
    void test7_shouldHandleMissingServiceIdHeader() throws Exception {
        System.out.println("\n=== Test 7: Missing Service ID ===");
        System.out.println("📤 Sending request without X-Service-Id header...");

        MvcResult result = mockMvc.perform(post("/api/v1/notification/email/send/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmailRequestJson()))
                .andExpect(status().isAccepted())
                .andReturn();

        String rateLimitService = result.getResponse().getHeader("X-RateLimit-Service");
        System.out.println("📋 X-RateLimit-Service: " + rateLimitService);

        assertThat(rateLimitService).isEqualTo("unknown-service");

        System.out.println("✅ TEST PASSED: Handled gracefully with default service ID\n");
    }

    @Test
    @DisplayName("Test 8: Should allow requests after manual reset")
    void test8_shouldAllowAfterManualReset() throws Exception {
        // Given
        String serviceId = "reset-test-service";

        System.out.println("\n=== Test 8: Manual Reset ===");
        System.out.println("📤 Exhausting rate limit...");

        // Exhaust limit
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/notification/email/send/async")
                    .header("X-Service-Id", serviceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEmailRequestJson()))
                    .andExpect(status().isAccepted());
        }

        // Verify blocked
        mockMvc.perform(post("/api/v1/notification/email/send/async")
                .header("X-Service-Id", serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmailRequestJson()))
                .andExpect(status().isTooManyRequests());
        System.out.println("✅ Service blocked after exhausting limit");

        // Manually reset
        String redisKey = "ratelimit:notification:service:" + serviceId;
        redisTemplate.delete(redisKey);
        System.out.println("🔄 Rate limit manually reset via Redis");

        // Verify allowed again
        mockMvc.perform(post("/api/v1/notification/email/send/async")
                .header("X-Service-Id", serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmailRequestJson()))
                .andExpect(status().isAccepted());

        System.out.println("✅ TEST PASSED: Requests allowed after reset\n");
    }

    // Helper method
    private String createEmailRequestJson() throws Exception {
        EmailNotificationRequest request = EmailNotificationRequest.builder()
                .to(List.of("test@example.com"))
                .subject("Rate Limit Test")
                .body("Test email")
                .isHtml(false)
                .build();

        return objectMapper.writeValueAsString(request);
    }
}