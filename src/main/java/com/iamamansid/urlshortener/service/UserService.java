package com.iamamansid.urlshortener.service;

import com.iamamansid.urlshortener.config.AuthProperties;
import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.entity.AuthProvider;
import com.iamamansid.urlshortener.entity.UserRole;
import com.iamamansid.urlshortener.exception.EmailAlreadyExistsException;
import com.iamamansid.urlshortener.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Account management: local registration and resolving the signed-in user.
 * The role is email-driven — addresses in {@code auth.admin-emails} are
 * ADMIN on both the password form and Google SSO.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProps;

    public UserService(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       AuthProperties authProps) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authProps = authProps;
    }

    @Transactional
    public AppUser register(String email, String rawPassword, String displayName) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw new EmailAlreadyExistsException(normalized);
        }
        AppUser user = new AppUser();
        user.setEmail(normalized);
        user.setDisplayName(displayName == null || displayName.isBlank() ? normalized : displayName.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(authProps.isAdminEmail(normalized) ? UserRole.ADMIN : UserRole.USER);
        AppUser saved = users.save(user);
        log.info("Registered {} account {}", saved.getRole(), normalized);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return users.findByEmail(email.trim().toLowerCase());
    }

    /**
     * The signed-in app user, if any. Works for both form login and Google
     * SSO because both principals expose the email via {@code getName()}.
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> currentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return findByEmail(authentication.getName());
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(Authentication authentication) {
        return currentUser(authentication)
                .map(u -> u.getRole() == UserRole.ADMIN)
                .orElse(false);
    }
}
