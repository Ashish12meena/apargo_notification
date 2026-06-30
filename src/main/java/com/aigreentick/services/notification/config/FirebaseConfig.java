package com.aigreentick.services.notification.config;


import java.io.IOException;
import java.io.InputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.aigreentick.services.notification.config.properties.FirebaseProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    private final FirebaseProperties firebaseProperties;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                initializeFirebase();
            } else {
                log.info("Firebase already initialized");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase", e);
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }

    private void initializeFirebase() throws IOException {
        log.info("Initializing Firebase with credentials: {}", firebaseProperties.getCredentialsPath());

        // Load from classpath
        InputStream serviceAccount = getClass().getClassLoader()
                .getResourceAsStream(firebaseProperties.getCredentialsPath());

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setProjectId(firebaseProperties.getProjectId());

        if (StringUtils.hasText(firebaseProperties.getDatabaseUrl())) {
            optionsBuilder.setDatabaseUrl(firebaseProperties.getDatabaseUrl());
        }

        if (StringUtils.hasText(firebaseProperties.getStorageBucket())) {
            optionsBuilder.setStorageBucket(firebaseProperties.getStorageBucket());
        }

        FirebaseApp.initializeApp(optionsBuilder.build());

        log.info("Firebase initialized successfully for project: {}", firebaseProperties.getProjectId());
    }

    @Bean
    public FirebaseApp firebaseApp() {
        return FirebaseApp.getInstance();
    }
}