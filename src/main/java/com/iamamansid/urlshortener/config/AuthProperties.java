package com.iamamansid.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Auth tunables. {@code admin-emails} lists the addresses that are granted
 * ROLE_ADMIN on signup/sign-in (comma-separated env {@code ADMIN_EMAILS}).
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        List<String> adminEmails) {

    public boolean isAdminEmail(String email) {
        if (email == null || adminEmails == null) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        return adminEmails.stream()
                .filter(e -> e != null)
                .map(e -> e.trim().toLowerCase())
                .anyMatch(normalized::equals);
    }
}
