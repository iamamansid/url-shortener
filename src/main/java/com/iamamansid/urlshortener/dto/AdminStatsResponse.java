package com.iamamansid.urlshortener.dto;

import java.time.Instant;
import java.util.List;

/**
 * Everything the admin dashboard renders: headline totals, 30-day series,
 * top links, newest links and newest users.
 */
public record AdminStatsResponse(
        Totals totals,
        List<DayCount> linksPerDay,
        List<DayCount> clicksPerDay,
        List<DayCount> pageViewsPerDay,
        List<TopLink> topLinks,
        List<TopLink> recentLinks,
        List<UserItem> recentUsers) {

    public record Totals(
            long users,
            long links,
            long clicks,
            long pageViews) {
    }

    public record DayCount(
            String date,
            long count) {
    }

    public record TopLink(
            String code,
            String shortUrl,
            String originalUrl,
            long clicks,
            String ownerEmail,
            Instant createdAt,
            Instant lastClickedAt) {
    }

    public record UserItem(
            String email,
            String displayName,
            String provider,
            String role,
            long linkCount,
            Instant createdAt) {
    }
}
