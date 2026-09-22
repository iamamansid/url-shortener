package com.iamamansid.urlshortener.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the redirect route pattern used in {@link RedirectController}:
 * single-segment codes match, while {@code /actuator/...} and multi-segment
 * API paths are left alone for their own handlers.
 */
class RedirectPatternTest {

    // Must stay in sync with RedirectController's @GetMapping pattern.
    private final PathPattern pattern =
            new PathPatternParser().parse("/{code:^(?!actuator$)[A-Za-z0-9_-]+$}");

    @Test
    void matchesGeneratedAndCustomCodes() {
        assertTrue(pattern.matches(PathContainer.parsePath("/0003d7")));
        assertTrue(pattern.matches(PathContainer.parsePath("/abc123")));
        assertTrue(pattern.matches(PathContainer.parsePath("/my-link_1")));
    }

    @Test
    void doesNotMatchActuator() {
        assertFalse(pattern.matches(PathContainer.parsePath("/actuator")));
    }

    @Test
    void doesNotMatchMultiSegmentPaths() {
        assertFalse(pattern.matches(PathContainer.parsePath("/api/v1/urls")));
        assertFalse(pattern.matches(PathContainer.parsePath("/actuator/health")));
    }

    @Test
    void extractsCodeVariable() {
        PathPattern.PathMatchInfo info = pattern.matchAndExtract(PathContainer.parsePath("/0003d7"));
        assertNotNull(info);
        assertEquals("0003d7", info.getUriVariables().get("code"));
    }
}
