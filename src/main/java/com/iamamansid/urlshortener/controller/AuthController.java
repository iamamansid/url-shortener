package com.iamamansid.urlshortener.controller;

import com.iamamansid.urlshortener.dto.AuthProvidersResponse;
import com.iamamansid.urlshortener.dto.MeResponse;
import com.iamamansid.urlshortener.dto.RegisterRequest;
import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Account endpoints. Sign-in itself is handled by Spring Security:
 * <ul>
 *   <li>email + password: {@code POST /api/v1/auth/login} (form fields
 *   {@code email} and {@code password}),</li>
 *   <li>Google SSO: {@code GET /oauth2/authorization/google}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Accounts", description = "Register, check session, and see available sign-in methods")
public class AuthController {

    private final UserService userService;
    private final String googleClientId;

    public AuthController(UserService userService,
                          @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId) {
        this.userService = userService;
        this.googleClientId = googleClientId;
    }

    @Operation(summary = "Create an account",
            description = "Registers with email + password. After this, sign in via POST /api/v1/auth/login.")
    @PostMapping("/register")
    public ResponseEntity<MeResponse> register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = userService.register(request.email(), request.password(), request.displayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toMe(user));
    }

    @Operation(summary = "Who am I", description = "Returns the signed-in user's profile, or 401.")
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return userService.currentUser(authentication)
                .map(this::toMe)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
    }

    @Operation(summary = "Available sign-in methods",
            description = "Google SSO is offered only when the OAuth client is configured.")
    @GetMapping("/providers")
    public AuthProvidersResponse providers() {
        return new AuthProvidersResponse(StringUtils.hasText(googleClientId));
    }

    private MeResponse toMe(AppUser user) {
        return new MeResponse(
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getProvider().name());
    }
}
