package com.iamamansid.urlshortener.exception;

import com.iamamansid.urlshortener.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies unmapped paths map to 404 instead of falling through to the 500 catch-all.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void noResourceFoundMapsTo404() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/does-not-exist");
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/does-not-exist");

        ResponseEntity<ErrorResponse> response = handler.handleNoResource(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().status());
        assertEquals("/does-not-exist", response.getBody().path());
        assertTrue(response.getBody().message().contains("/does-not-exist"));
    }
}
