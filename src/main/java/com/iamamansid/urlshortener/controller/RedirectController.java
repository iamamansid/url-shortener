package com.iamamansid.urlshortener.controller;

import com.iamamansid.urlshortener.service.UrlService;
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
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

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
