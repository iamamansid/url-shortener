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
import com.iamamansid.urlshortener.util.Base62;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Core URL shortening logic: validation, code generation, cache-aside reads,
 * and async click tracking.
 */
@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    private static final String CACHE_KEY_PREFIX = "url:code:";

    private final ShortUrlRepository repository;
    private final StringRedisTemplate redis;
    private final ClickEventProducer clickEventProducer;
    private final AppProperties props;

    public UrlService(ShortUrlRepository repository,
                      StringRedisTemplate redis,
                      ClickEventProducer clickEventProducer,
                      AppProperties props) {
        this.repository = repository;
        this.redis = redis;
        this.clickEventProducer = clickEventProducer;
        this.props = props;
    }

    /**
     * Creates a short URL. Generated codes come from the Base62-encoded
     * database id (insert first, then set code), so they are unique by
     * construction without a retry loop.
     */
    @Transactional
    public CreateUrlResponse createShortUrl(CreateUrlRequest request) {
        String originalUrl = normalizeAndValidate(request.url());

        String code = null;
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            code = validateAlias(request.customAlias().trim());
            if (repository.existsByCode(code)) {
                throw new AliasAlreadyExistsException(code);
            }
        }

        ShortUrl entity = new ShortUrl();
        entity.setOriginalUrl(originalUrl);
        entity.setCode(code);
        entity = repository.saveAndFlush(entity);

        if (code == null) {
            entity.setCode(generateUniqueCode(entity.getId()));
            entity = repository.save(entity);
        }

        cacheMapping(entity.getCode(), originalUrl);
        log.info("Created short URL: code={} -> {}", entity.getCode(), originalUrl);
        return new CreateUrlResponse(entity.getCode(), shortUrl(entity.getCode()), originalUrl);
    }

    /**
     * Cache-aside lookup: Redis first, Postgres on miss (then repopulate).
     */
    @Transactional(readOnly = true)
    public String getOriginalUrl(String code) {
        String cached = redis.opsForValue().get(cacheKey(code));
        if (cached != null) {
            return cached;
        }
        ShortUrl entity = repository.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        cacheMapping(code, entity.getOriginalUrl());
        return entity.getOriginalUrl();
    }

    /**
     * Fires a click event without blocking the caller (the redirect path).
     */
    public void recordClickAsync(String code) {
        clickEventProducer.publishClick(code);
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String code) {
        ShortUrl entity = repository.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        return new UrlStatsResponse(
                entity.getCode(),
                shortUrl(entity.getCode()),
                entity.getOriginalUrl(),
                entity.getClicks(),
                entity.getCreatedAt(),
                entity.getLastClickedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<UrlListItem> listUrls(Pageable pageable) {
        Page<ShortUrl> page = repository.findAll(pageable);
        return new PageResponse<>(
                page.map(e -> new UrlListItem(
                        e.getCode(),
                        shortUrl(e.getCode()),
                        e.getOriginalUrl(),
                        e.getClicks(),
                        e.getCreatedAt())).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Transactional
    public void deleteByCode(String code) {
        ShortUrl entity = repository.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        repository.delete(entity);
        redis.delete(cacheKey(code));
        log.info("Deleted short URL: code={}", code);
    }

    // ------------------------------------------------------------------ helpers

    private String generateUniqueCode(long id) {
        // Defensive loop: generated codes are unique by construction, but a
        // user-chosen custom alias could theoretically collide with one.
        String candidate = Base62.encode(id, props.codeMinLength());
        int attempt = 0;
        while (repository.existsByCode(candidate)) {
            candidate = Base62.encode(id, props.codeMinLength()) + Base62.encode(attempt++);
        }
        return candidate;
    }

    private String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("url must not be blank");
        }
        String trimmed = raw.trim();
        final URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Malformed URL: '" + trimmed + "'");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("URL must use http or https: '" + trimmed + "'");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL must contain a host: '" + trimmed + "'");
        }
        rejectSelfReference(uri);
        return uri.toASCIIString();
    }

    private void rejectSelfReference(URI uri) {
        try {
            URI base = new URI(props.baseUrl());
            if (base.getHost() != null
                    && base.getHost().equalsIgnoreCase(uri.getHost())
                    && effectivePort(base) == effectivePort(uri)) {
                throw new InvalidUrlException("URL must not point back to this shortening service");
            }
        } catch (URISyntaxException e) {
            log.warn("app.base-url is not a valid URI: {}", props.baseUrl());
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String validateAlias(String alias) {
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidUrlException(
                    "customAlias must be 3-32 characters of letters, digits, '-' or '_'");
        }
        return alias;
    }

    private void cacheMapping(String code, String originalUrl) {
        redis.opsForValue().set(cacheKey(code), originalUrl, Duration.ofHours(props.cacheTtlHours()));
    }

    private String shortUrl(String code) {
        String base = props.baseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + code;
    }

    static String cacheKey(String code) {
        return CACHE_KEY_PREFIX + code;
    }
}
