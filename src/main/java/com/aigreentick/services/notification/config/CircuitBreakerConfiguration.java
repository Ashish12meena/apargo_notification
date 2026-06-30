
package com.aigreentick.services.notification.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aigreentick.services.notification.config.properties.CircuitBreakerProperties;
import com.aigreentick.services.notification.config.properties.CircuitBreakerProperties.EmailProviderConfig;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerConfiguration {

        private final CircuitBreakerProperties circuitBreakerProperties;

        @Bean
        public CircuitBreakerRegistry circuitBreakerRegistry() {
                return CircuitBreakerRegistry.ofDefaults();
        }

        @Bean
        public CircuitBreaker smtpCircuitBreaker(CircuitBreakerRegistry registry) {
                EmailProviderConfig emailProviderConfig = circuitBreakerProperties
                                .getInstances()
                                .getEmailProvider();

                CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                                .slidingWindowSize(emailProviderConfig.getSlidingWindowSize())
                                .minimumNumberOfCalls(emailProviderConfig.getMinimumNumberOfCalls())
                                .failureRateThreshold(emailProviderConfig.getFailureRateThreshold())
                                .slowCallRateThreshold(emailProviderConfig.getSlowCallRateThreshold())
                                .slowCallDurationThreshold(
                                                Duration.ofMillis(emailProviderConfig
                                                                .getSlowCallDurationThresholdMs()))
                                .waitDurationInOpenState(
                                                Duration.ofMillis(emailProviderConfig
                                                                .getWaitDurationInOpenStateMs()))
                                .permittedNumberOfCallsInHalfOpenState(
                                                emailProviderConfig.getPermittedNumberOfCallsInHalfOpenState())
                                .automaticTransitionFromOpenToHalfOpenEnabled(
                                                emailProviderConfig
                                                                .isAutomaticTransitionFromOpenToHalfOpenEnabled())
                                .build();

                CircuitBreaker circuitBreaker = registry.circuitBreaker("smtpProvider", config);

                circuitBreaker.getEventPublisher()
                                .onStateTransition(event -> log.warn("SMTP Circuit Breaker state changed from {} to {}",
                                                event.getStateTransition().getFromState(),
                                                event.getStateTransition().getToState()))
                                .onError(event -> log.error("SMTP Circuit Breaker recorded error: {}",
                                                event.getThrowable().getMessage()));

                log.info("SMTP Circuit Breaker initialized - slidingWindowSize: {}, minimumCalls: {}, failureThreshold: {}%",
                                emailProviderConfig.getSlidingWindowSize(),
                                emailProviderConfig.getMinimumNumberOfCalls(),
                                emailProviderConfig.getFailureRateThreshold());

                return circuitBreaker;
        }
}
