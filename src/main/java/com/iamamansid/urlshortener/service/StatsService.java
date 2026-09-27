package com.iamamansid.urlshortener.service;

import com.iamamansid.urlshortener.config.AppProperties;
import com.iamamansid.urlshortener.dto.AdminStatsResponse;
import com.iamamansid.urlshortener.dto.AdminStatsResponse.DayCount;
import com.iamamansid.urlshortener.dto.AdminStatsResponse.TopLink;
import com.iamamansid.urlshortener.dto.AdminStatsResponse.Totals;
import com.iamamansid.urlshortener.dto.AdminStatsResponse.UserItem;
import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.entity.ShortUrl;
import com.iamamansid.urlshortener.repository.AppUserRepository;
import com.iamamansid.urlshortener.repository.LinkClickRepository;
import com.iamamansid.urlshortener.repository.PageViewRepository;
import com.iamamansid.urlshortener.repository.ShortUrlRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates everything the admin dashboard shows: headline totals, 30-day
 * series for links/clicks/visits, top and newest links, newest users.
 */
@Service
public class StatsService {

    private static final int SERIES_DAYS = 30;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ShortUrlRepository urls;
    private final AppUserRepository users;
    private final LinkClickRepository clicks;
    private final PageViewRepository pageViews;
    private final AppProperties props;

    public StatsService(ShortUrlRepository urls,
                        AppUserRepository users,
                        LinkClickRepository clicks,
                        PageViewRepository pageViews,
                        AppProperties props) {
        this.urls = urls;
        this.users = users;
        this.clicks = clicks;
        this.pageViews = pageViews;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse getAdminStats() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant since = today.minusDays(SERIES_DAYS - 1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Totals totals = new Totals(
                users.count(),
                urls.count(),
                urls.sumClicks(),
                pageViews.count());

        List<DayCount> linksPerDay = toSeries(today, urls.countByDay(since));
        List<DayCount> clicksPerDay = toSeries(today, clicks.countByDay(since));
        List<DayCount> pageViewsPerDay = toSeries(today, pageViews.countByDay(since));

        List<TopLink> topLinks = urls.findTopByClicks(PageRequest.of(0, 10))
                .stream().map(this::toTopLink).toList();

        List<TopLink> recentLinks = urls.findAll(
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().map(this::toTopLink).toList();

        List<UserItem> recentUsers = users.findAll(
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().map(this::toUserItem).toList();

        return new AdminStatsResponse(
                totals, linksPerDay, clicksPerDay, pageViewsPerDay, topLinks, recentLinks, recentUsers);
    }

    private TopLink toTopLink(ShortUrl s) {
        AppUser owner = s.getOwner();
        return new TopLink(
                s.getCode(),
                shortUrl(s.getCode()),
                s.getOriginalUrl(),
                s.getClicks(),
                owner != null ? owner.getEmail() : null,
                s.getCreatedAt(),
                s.getLastClickedAt());
    }

    private UserItem toUserItem(AppUser u) {
        return new UserItem(
                u.getEmail(),
                u.getDisplayName(),
                u.getProvider().name(),
                u.getRole().name(),
                urls.countByOwner(u),
                u.getCreatedAt());
    }

    private String shortUrl(String code) {
        String base = props.baseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + code;
    }

    /**
     * Turns sparse {@code [date, count]} rows into a dense 30-day series,
     * oldest first, filling missing days with zero.
     */
    private List<DayCount> toSeries(LocalDate today, List<Object[]> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = toLocalDate(row[0]);
            long count = ((Number) row[1]).longValue();
            byDay.merge(day, count, Long::sum);
        }
        List<DayCount> series = new ArrayList<>(SERIES_DAYS);
        for (int i = SERIES_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            series.add(new DayCount(day.format(DAY_FORMAT), byDay.getOrDefault(day, 0L)));
        }
        return series;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        throw new IllegalStateException("Unexpected day value type: " + value.getClass());
    }
}
