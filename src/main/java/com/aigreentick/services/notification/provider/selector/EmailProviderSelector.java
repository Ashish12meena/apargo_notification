package com.aigreentick.services.notification.provider.selector;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aigreentick.services.notification.config.properties.EmailProviderProperties;
import com.aigreentick.services.notification.enums.email.EmailProviderType;
import com.aigreentick.services.notification.exceptions.ProviderNotAvailableException;
import com.aigreentick.services.notification.provider.email.EmailProviderStrategy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EmailProviderSelector {
    private final Map<EmailProviderType, EmailProviderStrategy> providers;
    private final EmailProviderProperties properties;

    public EmailProviderSelector(List<EmailProviderStrategy> providerList,
            EmailProviderProperties properties) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        EmailProviderStrategy::getProviderType,
                        Function.identity()));
        this.properties = properties;
        log.info("Initialized EmailProviderSelector with providers: {}", providers.keySet());
    }

    public EmailProviderStrategy selectProvider() {
        EmailProviderType activeProviderType = properties.getActive();

        EmailProviderStrategy provider = providers.get(activeProviderType);

        if (provider != null && provider.isAvailable()) {   
            log.debug("Selected active provider: {}", activeProviderType);
            return provider;
        }

        throw new ProviderNotAvailableException("No email provider is currently available");
    }

    public EmailProviderStrategy getProvider(EmailProviderType providerType) {
        EmailProviderStrategy provider = providers.get(providerType);
        if (provider == null) {
            throw new ProviderNotAvailableException("Provider not found: " + providerType);
        }
        return provider;
    }

    public boolean isProviderAvailable(EmailProviderType providerType) {
        EmailProviderStrategy provider = providers.get(providerType);
        return provider != null && provider.isAvailable();
    }

     public Map<EmailProviderType, Boolean> getAllProviderStatuses() {
        return providers.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().isAvailable()
            ));
    }

}
