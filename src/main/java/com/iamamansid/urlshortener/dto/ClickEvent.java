package com.iamamansid.urlshortener.dto;

import java.time.Instant;

/**
 * Payload published to the {@code url-clicks} Kafka topic on every redirect.
 * The redirect path never waits for this — it is fire-and-forget.
 */
public record ClickEvent(
        String code,
        Instant clickedAt) {
}
