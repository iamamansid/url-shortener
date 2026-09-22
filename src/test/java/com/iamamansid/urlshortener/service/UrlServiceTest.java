package com.iamamansid.urlshortener.service;

import com.iamamansid.urlshortener.config.AppProperties;
import com.iamamansid.urlshortener.dto.CreateUrlRequest;
import com.iamamansid.urlshortener.dto.CreateUrlResponse;
import com.iamamansid.urlshortener.dto.PageResponse;
import com.iamamansid.urlshortener.dto.UrlListItem;
import com.iamamansid.urlshortener.dto.UrlStatsResponse;
import com.iamamansid.urlshortener.entity.ShortUrl;
import com.iamamansid.urlshortener.exception.AliasAlreadyExistsException;
import com.iamamansid.urlshortener.exception.InvalidUrlException;
import com.iamamansid.urlshortener.exception.UrlNotFoundException;
import com.iamamansid.urlshortener.kafka.ClickEventProducer;
import com.iamamansid.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

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

    // ---------------------------------------------------------- create

    @Test
    void createShortUrl_generatesCodeFromDatabaseId() {
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> {
            ShortUrl e = inv.getArgument(0);
            e.setId(12345L);
            return e;
        });
        when(repository.save(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.existsByCode(anyString())).thenReturn(false);

        CreateUrlResponse response =
                service.createShortUrl(new CreateUrlRequest("https://example.com/article", null));

        assertEquals("0003d7", response.code()); // Base62(12345) padded to 6
        assertEquals("http://localhost:8080/0003d7", response.shortUrl());
        assertEquals("https://example.com/article", response.originalUrl());
        verify(valueOps).set(eq("url:code:0003d7"), eq("https://example.com/article"), eq(Duration.ofHours(24)));
    }

    @Test
    void createShortUrl_acceptsCustomAlias() {
        when(repository.existsByCode("my-link")).thenReturn(false);
        // Custom aliases are persisted with a single insert (code set up-front).
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUrlResponse response =
                service.createShortUrl(new CreateUrlRequest("https://example.com", "my-link"));

        assertEquals("my-link", response.code());
        assertEquals("http://localhost:8080/my-link", response.shortUrl());
    }

    @Test
    void createShortUrl_rejectsTakenAlias() {
        when(repository.existsByCode("taken")).thenReturn(true);

        assertThrows(AliasAlreadyExistsException.class,
                () -> service.createShortUrl(new CreateUrlRequest("https://example.com", "taken")));
    }

    @Test
    void createShortUrl_rejectsBadAliasFormat() {
        // too short / illegal characters
        assertThrows(InvalidUrlException.class,
                () -> service.createShortUrl(new CreateUrlRequest("https://example.com", "ab")));
        assertThrows(InvalidUrlException.class,
                () -> service.createShortUrl(new CreateUrlRequest("https://example.com", "has space")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not a url", "ftp://example.com/file", "://missing-scheme", "http://"})
    void createShortUrl_rejectsInvalidUrls(String badUrl) {
        assertThrows(InvalidUrlException.class,
                () -> service.createShortUrl(new CreateUrlRequest(badUrl, null)));
    }

    @Test
    void createShortUrl_rejectsSelfReference() {
        assertThrows(InvalidUrlException.class,
                () -> service.createShortUrl(new CreateUrlRequest("http://localhost:8080/abc123", null)));
    }

    // ---------------------------------------------------------- resolve

    @Test
    void getOriginalUrl_servesFromCacheWithoutTouchingDb() {
        when(valueOps.get("url:code:abc123")).thenReturn("https://example.com");

        assertEquals("https://example.com", service.getOriginalUrl("abc123"));
        verify(repository, never()).findByCode(anyString());
    }

    @Test
    void getOriginalUrl_fallsBackToDbAndRepopulatesCache() {
        when(valueOps.get("url:code:abc123")).thenReturn(null);
        ShortUrl entity = entity("abc123", "https://example.com/db");
        when(repository.findByCode("abc123")).thenReturn(Optional.of(entity));

        assertEquals("https://example.com/db", service.getOriginalUrl("abc123"));
        verify(valueOps).set(eq("url:code:abc123"), eq("https://example.com/db"), eq(Duration.ofHours(24)));
    }

    @Test
    void getOriginalUrl_unknownCodeThrows() {
        when(valueOps.get("url:code:nope")).thenReturn(null);
        when(repository.findByCode("nope")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> service.getOriginalUrl("nope"));
    }

    // ---------------------------------------------------------- stats / list / delete

    @Test
    void getStats_returnsCounters() {
        ShortUrl entity = entity("abc123", "https://example.com");
        entity.setClicks(42);
        entity.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        entity.setLastClickedAt(Instant.parse("2026-02-01T00:00:00Z"));
        when(repository.findByCode("abc123")).thenReturn(Optional.of(entity));

        UrlStatsResponse stats = service.getStats("abc123");

        assertEquals("abc123", stats.code());
        assertEquals(42, stats.clicks());
        assertEquals("http://localhost:8080/abc123", stats.shortUrl());
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), stats.lastClickedAt());
    }

    @Test
    void getStats_unknownCodeThrows() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());
        assertThrows(UrlNotFoundException.class, () -> service.getStats("nope"));
    }

    @Test
    void listUrls_mapsPage() {
        ShortUrl entity = entity("abc123", "https://example.com");
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        PageResponse<UrlListItem> result = service.listUrls(PageRequest.of(0, 20));

        assertEquals(1, result.items().size());
        assertEquals("abc123", result.items().get(0).code());
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());
    }

    @Test
    void deleteByCode_removesRowAndEvictsCache() {
        ShortUrl entity = entity("abc123", "https://example.com");
        when(repository.findByCode("abc123")).thenReturn(Optional.of(entity));

        service.deleteByCode("abc123");

        verify(repository).delete(entity);
        verify(redis).delete("url:code:abc123");
    }

    @Test
    void deleteByCode_unknownCodeThrows() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());
        assertThrows(UrlNotFoundException.class, () -> service.deleteByCode("nope"));
    }

    // ---------------------------------------------------------- helpers

    private static ShortUrl entity(String code, String originalUrl) {
        ShortUrl e = new ShortUrl();
        e.setId(1L);
        e.setCode(code);
        e.setOriginalUrl(originalUrl);
        return e;
    }
}
