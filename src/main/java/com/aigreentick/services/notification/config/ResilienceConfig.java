package com.aigreentick.services.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aigreentick.services.notification.config.properties.EmailRetryProperties;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {

        private final EmailRetryProperties emailRetryProperties;

        /**
         * Central Retry Registry - manages all retry instances
         */
        @Bean
        public RetryRegistry retryRegistry() {
                return RetryRegistry.ofDefaults();
        }

        /**
         * Email-specific Retry Configuration
         * Used for: Email delivery, template processing, provider calls
         */
        @Bean
        public Retry emailRetry(RetryRegistry retryRegistry) {

                IntervalFunction intervalFunction = IntervalFunction.ofExponentialBackoff(
                                emailRetryProperties.getInitialDelayMs(), // initial delay (ms)
                                emailRetryProperties.getMultiplier(), // multiplier
                                emailRetryProperties.getMaxDelayMs() // max delay (ms)
                );

                RetryConfig config = RetryConfig.custom()
                                .maxAttempts(emailRetryProperties.getMaxAttempts())
                                .intervalFunction(intervalFunction) // This already handles the delay
                                .retryExceptions(
                                                org.springframework.mail.MailException.class,
                                                jakarta.mail.MessagingException.class,
                                                java.io.IOException.class,
                                                RuntimeException.class)
                                .ignoreExceptions(
                                                IllegalArgumentException.class,
                                                IllegalStateException.class)
                                .build();

                Retry retry = retryRegistry.retry("emailRetry", config);

                // ✅ Logging hooks remain the same
                retry.getEventPublisher()
                                .onRetry(event -> log.warn("Email retry attempt {} of {}. Reason: {}",
                                                event.getNumberOfRetryAttempts(),
                                                emailRetryProperties.getMaxAttempts(),
                                                event.getLastThrowable() != null
                                                                ? event.getLastThrowable().getMessage()
                                                                : "unknown"))
                                .onSuccess(event -> {
                                        if (event.getNumberOfRetryAttempts() > 0) {
                                                log.info("Email succeeded after {} retry attempt(s)",
                                                                event.getNumberOfRetryAttempts());
                                        }
                                })
                                .onError(event -> log.error("Email operation failed after {} attempts. Final error: {}",
                                                event.getNumberOfRetryAttempts(),
                                                event.getLastThrowable() != null
                                                                ? event.getLastThrowable().getMessage()
                                                                : "unknown"));

                return retry;
        }
}
