package com.iamamansid.urlshortener.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the root landing endpoint returns service metadata instead of an error.
 */
class RootControllerTest {

    @Test
    void rootReturnsServiceMetadata() {
        Map<String, String> body = new RootController().root();

        assertEquals("url-shortener", body.get("service"));
        assertEquals("ok", body.get("status"));
        assertTrue(body.get("create").contains("POST /api/v1/urls"));
        assertTrue(body.get("redirect").contains("GET /{code}"));
        assertTrue(body.get("health").contains("/actuator/health"));
    }
}
