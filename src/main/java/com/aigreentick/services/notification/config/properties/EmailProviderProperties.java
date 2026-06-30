package com.aigreentick.services.notification.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import com.aigreentick.services.notification.enums.email.EmailProviderType;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "email.provider")
@Data
@Validated
public class EmailProviderProperties {

    @NotNull
    private EmailProviderType active = EmailProviderType.SMTP;
}
