package com.iamamansid.urlshortener.controller;

import com.iamamansid.urlshortener.dto.CreateUrlRequest;
import com.iamamansid.urlshortener.dto.CreateUrlResponse;
import com.iamamansid.urlshortener.dto.PageResponse;
import com.iamamansid.urlshortener.dto.UrlListItem;
import com.iamamansid.urlshortener.dto.UrlStatsResponse;
import com.iamamansid.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "Short links", description = "Create short links, browse them, and see how many times each was clicked")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @Operation(summary = "Create a short link",
            description = "Paste any long URL (optionally pick your own custom code) and get back a short link you can share.")
    @PostMapping
    public ResponseEntity<CreateUrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(urlService.createShortUrl(request));
    }

    @Operation(summary = "List all short links", description = "Newest links first.")
    @GetMapping
    public PageResponse<UrlListItem> listUrls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return urlService.listUrls(pageable);
    }

    @Operation(summary = "See click stats for a link",
            description = "Shows the original URL and how many times the short link was opened.")
    @GetMapping("/{code}/stats")
    public UrlStatsResponse getStats(@PathVariable String code) {
        return urlService.getStats(code);
    }

    @Operation(summary = "Delete a short link")
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        urlService.deleteByCode(code);
    }
}
