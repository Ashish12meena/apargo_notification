package com.aigreentick.services.notification.provider.selector;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aigreentick.services.notification.config.properties.PushProperties;
import com.aigreentick.services.notification.enums.push.DevicePlatform;
import com.aigreentick.services.notification.enums.push.PushProviderType;
import com.aigreentick.services.notification.exceptions.ProviderNotAvailableException;
import com.aigreentick.services.notification.provider.push.PushProviderStrategy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PushProviderSelector {
    
    private final Map<PushProviderType, PushProviderStrategy> providers;
    private final PushProperties properties;
    
    public PushProviderSelector(List<PushProviderStrategy> providerList,
                                PushProperties properties) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        PushProviderStrategy::getProviderType,
                        Function.identity()));
        this.properties = properties;
        
        log.info("Initialized PushProviderSelector with providers: {}", providers.keySet());
    }
    
    /**
     * Select provider based on active configuration
     */
    public PushProviderStrategy selectProvider() {
        PushProviderType activeProviderType = properties.getActive();
        
        PushProviderStrategy provider = providers.get(activeProviderType);
        
        if (provider != null && provider.isAvailable()) {
            log.debug("Selected active push provider: {}", activeProviderType);
            return provider;
        }
        
        log.warn("Active provider {} not available, attempting fallback", activeProviderType);
        
        PushProviderStrategy fallbackProvider = providers.values().stream()
                .filter(PushProviderStrategy::isAvailable)
                .sorted((p1, p2) -> Integer.compare(p2.getPriority(), p1.getPriority()))
                .findFirst()
                .orElse(null);
        
        if (fallbackProvider != null) {
            log.warn("Using fallback provider: {}", fallbackProvider.getProviderType());
            return fallbackProvider;
        }
        
        throw new ProviderNotAvailableException("No push provider is currently available");
    }
    
    /**
     * Select provider based on device platform
     * iOS -> APNs (if available), else FCM
     * Android -> FCM
     * Web -> Web Push (if available), else FCM
     */
    public PushProviderStrategy selectProviderByPlatform(DevicePlatform platform) {
        log.debug("Selecting push provider for platform: {}", platform);
        
        PushProviderStrategy provider = null;
        
        switch (platform) {
            case IOS:
                // Try APNs first for iOS
                provider = providers.get(PushProviderType.APNS);
                if (provider != null && provider.isAvailable()) {
                    log.debug("Selected APNs for iOS device");
                    return provider;
                }
                // Fallback to FCM (supports iOS too)
                provider = providers.get(PushProviderType.FCM);
                if (provider != null && provider.isAvailable()) {
                    log.debug("Falling back to FCM for iOS device");
                    return provider;
                }
                break;
                
            case ANDROID:
                // FCM is primary for Android
                provider = providers.get(PushProviderType.FCM);
                if (provider != null && provider.isAvailable()) {
                    log.debug("Selected FCM for Android device");
                    return provider;
                }
                break;
                
            case WEB:
                // Try Web Push first
                provider = providers.get(PushProviderType.WEB_PUSH);
                if (provider != null && provider.isAvailable()) {
                    log.debug("Selected Web Push for web device");
                    return provider;
                }
                // Fallback to FCM (supports web too)
                provider = providers.get(PushProviderType.FCM);
                if (provider != null && provider.isAvailable()) {
                    log.debug("Falling back to FCM for web device");
                    return provider;
                }
                break;
        }
        
        // If no platform-specific provider available, use active provider
        log.warn("No platform-specific provider available for {}, using active provider", platform);
        return selectProvider();
    }
    
    public PushProviderStrategy getProvider(PushProviderType providerType) {
        PushProviderStrategy provider = providers.get(providerType);
        if (provider == null) {
            throw new ProviderNotAvailableException("Provider not found: " + providerType);
        }
        return provider;
    }
    
    public boolean isProviderAvailable(PushProviderType providerType) {
        PushProviderStrategy provider = providers.get(providerType);
        return provider != null && provider.isAvailable();
    }
    
    public Map<PushProviderType, Boolean> getAllProviderStatuses() {
        return providers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().isAvailable()
                ));
    }
}