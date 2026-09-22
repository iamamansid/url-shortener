package com.iamamansid.urlshortener.dto;

import java.time.Instant;

public record UrlListItem(
        String code,
        String shortUrl,
        String originalUrl,
        long clicks,
        Instant createdAt) {
}
