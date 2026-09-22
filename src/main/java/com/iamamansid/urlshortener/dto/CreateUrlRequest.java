package com.iamamansid.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUrlRequest(
        @NotBlank(message = "url must not be blank") String url,
        String customAlias) {
}
