package com.iamamansid.urlshortener.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Local account registration. The email decides the role: addresses listed in
 * {@code auth.admin-emails} are created as ADMIN, everyone else as USER.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 120) String displayName) {
}
