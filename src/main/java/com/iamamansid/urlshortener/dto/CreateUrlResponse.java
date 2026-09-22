package com.iamamansid.urlshortener.dto;

public record CreateUrlResponse(
        String code,
        String shortUrl,
        String originalUrl) {
}
