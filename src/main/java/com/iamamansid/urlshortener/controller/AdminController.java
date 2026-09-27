package com.iamamansid.urlshortener.controller;

import com.iamamansid.urlshortener.dto.AdminStatsResponse;
import com.iamamansid.urlshortener.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only analytics. Access is enforced by Spring Security
 * ({@code /api/v1/admin/**} requires ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Site-wide analytics for admins")
public class AdminController {

    private final StatsService statsService;

    public AdminController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "Admin dashboard stats",
            description = "Headline totals, 30-day series for links/clicks/visits, top and newest links, newest users.")
    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return statsService.getAdminStats();
    }
}
