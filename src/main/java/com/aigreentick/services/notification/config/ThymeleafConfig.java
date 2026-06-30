package com.aigreentick.services.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import com.aigreentick.services.notification.config.properties.EmailProperties;
import com.aigreentick.services.notification.config.properties.EmailTemplateProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ThymeleafConfig {
    
    private final EmailTemplateProperties templateProperties;
    private final EmailProperties emailProperties;

    @Bean(name = "stringTemplateEngine")
    public TemplateEngine stringTemplateEngine() {
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.valueOf(templateProperties.getMode()));
        templateResolver.setOrder(2);
        
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver);
        
        log.info("String Template Engine configured ");
        
        return engine;
    }

    @Bean(name = "fileTemplateEngine")
    public TemplateEngine fileTemplateEngine() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix(templateProperties.getPrefix());
        templateResolver.setSuffix(templateProperties.getSuffix());
        templateResolver.setTemplateMode(TemplateMode.valueOf(templateProperties.getMode()));
        templateResolver.setCharacterEncoding(emailProperties.getEncoding());
        templateResolver.setCheckExistence(true);
        templateResolver.setOrder(1);
        
        
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver);
        
        log.info("File Template Engine configured with prefix={}, suffix={}, cache={}, ttl={}s",
                templateProperties.getPrefix(),
                templateProperties.getSuffix());
        
        return engine;
    }
}