package com.iamamansid.urlshortener.dto;

/**
 * Which sign-in methods are available. Google SSO is offered only when the
 * OAuth client is configured (env {@code GOOGLE_CLIENT_ID}).
 */
public record AuthProvidersResponse(boolean googleEnabled) {
}
