package com.iamamansid.urlshortener.service;

import com.iamamansid.urlshortener.config.AppProperties;
import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.entity.ShortUrl;
import com.iamamansid.urlshortener.exception.UrlNotFoundException;
import com.iamamansid.urlshortener.kafka.ClickEventProducer;
import com.iamamansid.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ownership rules for link deletion: users may delete only their own links,
 * admins may delete any link, anonymous links are admin-only.
 */
@ExtendWith(MockitoExtension.class)
class UrlOwnershipTest {

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ClickEventProducer producer;

    @Mock
    private ValueOperations<String, String> valueOps;

    private UrlService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new UrlService(repository, redis, producer,
                new AppProperties("http://localhost:8080", 20, 24, "url-clicks", 6, true));
    }

    private static AppUser user(long id) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail("user" + id + "@example.com");
        return u;
    }

    private static ShortUrl linkOwnedBy(AppUser owner) {
        ShortUrl s = new ShortUrl();
        s.setId(1L);
        s.setCode("abc123");
        s.setOriginalUrl("https://example.com");
        s.setOwner(owner);
        return s;
    }

    @Test
    void deleteByCode_ownerCanDeleteOwnLink() {
        AppUser owner = user(7L);
        when(repository.findByCode("abc123")).thenReturn(Optional.of(linkOwnedBy(owner)));

        service.deleteByCode("abc123", owner, false);

        verify(repository).delete(org.mockito.ArgumentMatchers.any(ShortUrl.class));
    }

    @Test
    void deleteByCode_otherUserCannotDelete() {
        when(repository.findByCode("abc123")).thenReturn(Optional.of(linkOwnedBy(user(7L))));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteByCode("abc123", user(9L), false));
        verify(repository, never()).delete(org.mockito.ArgumentMatchers.any(ShortUrl.class));
    }

    @Test
    void deleteByCode_adminCanDeleteAnyLink() {
        AppUser admin = user(1L);
        when(repository.findByCode("abc123")).thenReturn(Optional.of(linkOwnedBy(user(7L))));

        service.deleteByCode("abc123", admin, true);

        verify(repository).delete(org.mockito.ArgumentMatchers.any(ShortUrl.class));
    }

    @Test
    void deleteByCode_anonymousLinkIsAdminOnly() {
        when(repository.findByCode("abc123")).thenReturn(Optional.of(linkOwnedBy(null)));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteByCode("abc123", user(9L), false));

        service.deleteByCode("abc123", user(1L), true);
        verify(repository).delete(org.mockito.ArgumentMatchers.any(ShortUrl.class));
    }

    @Test
    void deleteByCode_missingLinkThrowsNotFound() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class,
                () -> service.deleteByCode("nope", user(7L), false));
    }
}
