package com.iamamansid.urlshortener.security;

import com.iamamansid.urlshortener.config.AuthProperties;
import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.entity.AuthProvider;
import com.iamamansid.urlshortener.entity.UserRole;
import com.iamamansid.urlshortener.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * On first Google sign-in, provisions an {@link AppUser} for the Google
 * account email. Addresses in {@code auth.admin-emails} become ADMIN —
 * that is how the site owner's Gmail gets the admin role.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final AppUserRepository users;
    private final AuthProperties authProps;

    public CustomOAuth2UserService(AppUserRepository users, AuthProperties authProps) {
        this.users = users;
        this.authProps = authProps;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oauth2User.getAttributes();

        Object emailAttr = attributes.get("email");
        if (emailAttr == null) {
            throw new OAuth2AuthenticationException("Google account did not provide an email address");
        }
        String email = emailAttr.toString().trim().toLowerCase();
        String name = attributes.get("name") != null ? attributes.get("name").toString() : email;

        AppUser user = users.findByEmail(email).orElseGet(() -> {
            AppUser created = new AppUser();
            created.setEmail(email);
            created.setDisplayName(name);
            created.setProvider(AuthProvider.GOOGLE);
            created.setRole(authProps.isAdminEmail(email) ? UserRole.ADMIN : UserRole.USER);
            AppUser saved = users.save(created);
            log.info("Provisioned {} user {} via Google SSO", saved.getRole(), email);
            return saved;
        });

        // Role can change via auth.admin-emails without a redeploy of user rows.
        UserRole expected = authProps.isAdminEmail(email) ? UserRole.ADMIN : user.getRole();
        if (expected == UserRole.ADMIN && user.getRole() != UserRole.ADMIN) {
            user.setRole(UserRole.ADMIN);
            user = users.save(user);
            log.info("Granted ADMIN to {} via Google SSO (auth.admin-emails)", email);
        }

        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new OAuth2AppUser(authorities, attributes);
    }
}
