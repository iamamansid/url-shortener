package com.iamamansid.urlshortener.controller;

import com.iamamansid.urlshortener.dto.PageResponse;
import com.iamamansid.urlshortener.dto.UrlListItem;
import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.service.UrlService;
import com.iamamansid.urlshortener.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own data — powers the user dashboard.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "My links", description = "Links created by the signed-in user")
public class MeController {

    private final UrlService urlService;
    private final UserService userService;

    public MeController(UrlService urlService, UserService userService) {
        this.urlService = urlService;
        this.userService = userService;
    }

    @Operation(summary = "My links", description = "Links created by the signed-in account, newest first.")
    @GetMapping("/urls")
    public PageResponse<UrlListItem> myUrls(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AppUser me = userService.currentUser(authentication)
                .orElseThrow(() -> new AccessDeniedException("Sign in to see your links"));
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return urlService.listUrlsForOwner(me, pageable);
    }
}
