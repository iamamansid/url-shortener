package com.iamamansid.urlshortener.dto;

/**
 * The signed-in user's profile, returned by login/register/me endpoints.
 */
public record MeResponse(
        String email,
        String displayName,
        String role,
        String provider) {
}
