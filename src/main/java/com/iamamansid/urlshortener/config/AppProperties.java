package com.iamamansid.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level tunables, all overridable via environment variables.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        int rateLimitPerMinute,
        long cacheTtlHours,
        String clickTopic,
        int codeMinLength,
        boolean kafkaEnabled) {
}
