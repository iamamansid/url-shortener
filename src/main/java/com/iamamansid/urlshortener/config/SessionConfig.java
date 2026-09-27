package com.iamamansid.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * HTTP session cookie for the Redis-backed session (Spring Session manages
 * the cookie itself, so {@code server.servlet.session.cookie.*} does not
 * apply). Named {@code SESSION}, HTTPS-only on production, SameSite=Lax so
 * the Google OAuth2 round-trip keeps working.
 */
@Configuration
public class SessionConfig {

    @Value("${SESSION_COOKIE_SECURE:true}")
    private boolean secureCookie;

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
