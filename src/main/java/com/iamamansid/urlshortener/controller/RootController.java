package com.iamamansid.urlshortener.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Root path redirects to the interactive API docs so the base URL
 * (e.g. the "Live Demo" link) opens something even a non-technical
 * visitor can understand and try out.
 */
@RestController
public class RootController {

    @Hidden
    @GetMapping("/")
    public ResponseEntity<Void> root() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/swagger-ui/index.html"))
                .build();
    }
}
