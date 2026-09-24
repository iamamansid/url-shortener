package com.iamamansid.urlshortener.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root landing endpoint. Returns service metadata as JSON so the base URL
 * (e.g. the "Live Demo" link) shows something useful instead of an error.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, String> root() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("service", "url-shortener");
        info.put("status", "ok");
        info.put("create", "POST /api/v1/urls  {\"url\": \"https://example.com\"}");
        info.put("redirect", "GET /{code}");
        info.put("stats", "GET /api/v1/urls/{code}/stats");
        info.put("health", "/actuator/health");
        info.put("source", "https://github.com/iamamansid/url-shortener");
        return info;
    }
}
