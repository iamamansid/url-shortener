package com.iamamansid.urlshortener.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iamamansid.urlshortener.dto.MeResponse;
import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

/**
 * Session-based auth for the browser UI. Two sign-in paths:
 * <ul>
 *   <li>email + password: {@code POST /api/v1/auth/login} with form fields
 *   {@code email} and {@code password} (JSON success/error bodies),</li>
 *   <li>Google SSO (OAuth2, free): {@code /oauth2/authorization/google}.</li>
 * </ul>
 * CSRF is disabled: the API is same-origin and consumed only by our own
 * pages. Roles come from {@code app_users.role}; addresses in
 * {@code auth.admin-emails} are ADMIN.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService oauth2UserService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final ObjectMapper objectMapper;

    public SecurityConfig(CustomOAuth2UserService oauth2UserService,
                          LoginSuccessHandler loginSuccessHandler,
                          ObjectMapper objectMapper) {
        this.oauth2UserService = oauth2UserService;
        this.loginSuccessHandler = loginSuccessHandler;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/me/**").authenticated()
                        // Public API: auth endpoints, link create/list/stats stay open
                        // (delete is ownership-checked in the controller).
                        .requestMatchers("/api/v1/auth/**", "/api/v1/urls/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(
                                "/", "/index.html", "/login.html", "/dashboard.html", "/admin.html",
                                "/styles.css", "/app.js", "/login.js", "/dashboard.js", "/admin.js",
                                "/favicon.ico")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Everything else (incl. /{code} redirects and Swagger UI) stays public.
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/api/v1/auth/login")
                        .usernameParameter("email")
                        .successHandler((request, response, authentication) -> {
                            AppUser user = ((AppUserDetails) authentication.getPrincipal()).getUser();
                            writeJson(response, HttpServletResponse.SC_OK, new MeResponse(
                                    user.getEmail(),
                                    user.getDisplayName(),
                                    user.getRole().name(),
                                    user.getProvider().name()));
                        })
                        .failureHandler((request, response, exception) -> {
                            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                    new MeResponse(null, null, null, null));
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeJson(response, HttpServletResponse.SC_OK,
                                        java.util.Map.of("signedOut", true)))
                        .deleteCookies("SESSION"))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService))
                        .successHandler(loginSuccessHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        java.util.Map.of("error", "unauthenticated"));
                            } else {
                                response.sendRedirect("/login.html");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        java.util.Map.of("error", "forbidden"));
                            } else {
                                response.sendRedirect("/login.html");
                            }
                        }));

        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }

    @Bean
    public UserDetailsService userDetailsService(AppUserRepository users) {
        return email -> users.findByEmail(email.trim().toLowerCase())
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
