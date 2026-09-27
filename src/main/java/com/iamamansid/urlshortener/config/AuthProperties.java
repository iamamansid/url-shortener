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
        String normalized = canonicalize(email);
        return adminEmails.stream()
                .filter(e -> e != null)
                .map(AuthProperties::canonicalize)
                .anyMatch(normalized::equals);
    }

    /**
     * Lower-cases, trims, and — for Gmail addresses — drops dots from the
     * local part, because Gmail treats {@code aman.siddiqui114@gmail.com} and
     * {@code amansiddiqui114@gmail.com} as the same account. Without this,
     * an admin allow-list entry with dots would not match the dotless form
     * Google sometimes returns.
     */
    private static String canonicalize(String email) {
        String lower = email.trim().toLowerCase();
        int at = lower.lastIndexOf('@');
        if (at < 0) {
            return lower;
        }
        String local = lower.substring(0, at);
        String domain = lower.substring(at + 1);
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            local = local.replace(".", "");
            domain = "gmail.com";
        }
        return local + "@" + domain;
    }
}
