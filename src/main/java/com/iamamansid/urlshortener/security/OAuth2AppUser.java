package com.iamamansid.urlshortener.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;

/**
 * OAuth2 principal whose {@link #getName()} is the Google account email, so
 * controllers can resolve the current user uniformly via
 * {@code authentication.getName()} for both form and SSO logins.
 */
public class OAuth2AppUser extends DefaultOAuth2User {

    public OAuth2AppUser(Collection<? extends GrantedAuthority> authorities,
                         Map<String, Object> attributes) {
        super(authorities, attributes, "email");
    }
}
