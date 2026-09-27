package com.iamamansid.urlshortener.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

/**
 * Builds the OAuth2 client registry by hand so Google SSO is genuinely
 * optional. Boot's own auto-configuration eagerly builds a
 * {@link ClientRegistration} for every entry under
 * {@code spring.security.oauth2.client.registration.*}, and the builder
 * rejects an empty client-id — so leaving {@code GOOGLE_CLIENT_ID} unset
 * would crash startup. Defining this bean makes Boot's repository back off
 * (via {@code @ConditionalOnMissingBean}); when no credentials are present
 * the repository simply resolves nothing and the app starts normally.
 */
@Configuration
public class OAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret:}") String clientSecret,
            @Value("${spring.security.oauth2.client.registration.google.scope:openid,profile,email}") String scopeCsv) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)) {
            // Mirrors Boot's built-in "google" common provider; endpoints are
            // hardcoded so startup needs no OIDC discovery round-trip.
            String[] scopes = scopeCsv.split("\\s*,\\s*");
            registrations.add(ClientRegistration.withRegistrationId("google")
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope(scopes)
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .userNameAttributeName("sub")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .clientName("Google")
                    .build());
        }
        if (registrations.isEmpty()) {
            // InMemoryClientRegistrationRepository rejects an empty list, so
            // expose a repository that simply resolves nothing.
            return registrationId -> null;
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }
}
