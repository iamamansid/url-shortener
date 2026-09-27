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
 * optional. Boot's {@code OAuth2ClientProperties} validation rejects an
 * empty client-id for any registration declared under
 * {@code spring.security.oauth2.client.registration.*}, so the Google
 * registration is <em>not</em> declared in {@code application.yml} at all.
 * Instead this bean reads {@code GOOGLE_CLIENT_ID}/{@code GOOGLE_CLIENT_SECRET}
 * straight from the environment: when both are present the Google SSO button
 * appears, otherwise the repository resolves nothing and the app starts
 * normally with form login only. Defining this bean also makes Boot's own
 * repository back off (via {@code @ConditionalOnMissingBean}).
 */
@Configuration
public class OAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)) {
            // Mirrors Boot's built-in "google" common provider; endpoints are
            // hardcoded so startup needs no OIDC discovery round-trip.
            registrations.add(ClientRegistration.withRegistrationId("google")
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
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
