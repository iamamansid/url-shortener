package com.iamamansid.urlshortener.service;

import com.iamamansid.urlshortener.config.AuthProperties;
import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.entity.AuthProvider;
import com.iamamansid.urlshortener.entity.UserRole;
import com.iamamansid.urlshortener.exception.EmailAlreadyExistsException;
import com.iamamansid.urlshortener.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for local registration and role assignment.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository users;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private UserService serviceWithAdmins(String... adminEmails) {
        return new UserService(users, passwordEncoder, new AuthProperties(List.of(adminEmails)));
    }

    @Test
    void register_createsUserWithHashedPassword() {
        UserService service = serviceWithAdmins("boss@example.com");
        when(users.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("s3cret!!")).thenReturn("HASHED");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser saved = service.register("  Jane@Example.com ", "s3cret!!", "Jane");

        assertEquals("jane@example.com", saved.getEmail());
        assertEquals("HASHED", saved.getPasswordHash());
        assertEquals(AuthProvider.LOCAL, saved.getProvider());
        assertEquals(UserRole.USER, saved.getRole());
        assertEquals("Jane", saved.getDisplayName());
    }

    @Test
    void register_grantsAdminToAdminEmails() {
        UserService service = serviceWithAdmins("aman.siddiqui114@gmail.com");
        when(users.existsByEmail("aman.siddiqui114@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("HASHED");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser saved = service.register("Aman.Siddiqui114@Gmail.com", "pw", null);

        assertEquals(UserRole.ADMIN, saved.getRole());
        // Blank display name falls back to the email.
        assertEquals("aman.siddiqui114@gmail.com", saved.getDisplayName());
    }

    @Test
    void register_rejectsDuplicateEmail() {
        UserService service = serviceWithAdmins();
        when(users.existsByEmail("jane@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> service.register("jane@example.com", "s3cret!!", "Jane"));
    }

    @Test
    void currentUser_emptyForAnonymous() {
        UserService service = serviceWithAdmins();
        assertTrue(service.currentUser(null).isEmpty());

        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous",
                Set.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertTrue(service.currentUser(anonymous).isEmpty());
    }

    @Test
    void currentUser_resolvesByEmail() {
        UserService service = serviceWithAdmins();
        AppUser user = new AppUser();
        user.setEmail("jane@example.com");
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        var principal = new User("jane@example.com", "x",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        assertEquals("jane@example.com", service.currentUser(auth).orElseThrow().getEmail());
        assertTrue(service.isAdmin(auth) == false);
    }

    @Test
    void register_savesWhatWasValidated() {
        UserService service = serviceWithAdmins();
        when(users.existsByEmail("a@b.co")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("H");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register("a@b.co", "password1", "A B");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        assertEquals("H", captor.getValue().getPasswordHash());
    }
}
