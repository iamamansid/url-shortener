package com.iamamansid.urlshortener.controller;

import com.iamamansid.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Redirect endpoint. The pattern excludes {@code actuator} so
 * {@code /actuator/health} keeps working.
 */
@RestController
@Tag(name = "Open a short link", description = "Visiting a short code redirects to the original page and counts one click")
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    @Operation(summary = "Open a short link",
            description = "Put a short code after the site address — you will be redirected to the original page.")
    @GetMapping("/{code:^(?!actuator$)[A-Za-z0-9_-]+$}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = urlService.getOriginalUrl(code);
        // Fire-and-forget: the click event must never slow down the redirect.
        urlService.recordClickAsync(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}
