package com.iamamansid.urlshortener.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the root path redirects to the interactive API docs (Swagger UI).
 */
class RootControllerTest {

    @Test
    void rootRedirectsToSwaggerUi() {
        ResponseEntity<Void> response = new RootController().root();

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("/swagger-ui/index.html", response.getHeaders().getLocation().toString());
    }
}
