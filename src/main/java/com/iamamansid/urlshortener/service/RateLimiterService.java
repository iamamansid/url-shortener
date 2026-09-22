package com.iamamansid.urlshortener.service;

import com.iamamansid.urlshortener.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-key token buckets (Bucket4j). For a single instance this is
 * exact; for multi-instance deploys, swap the map for a Redis-backed bucket
 * (see README "Future improvements").
 */
@Service
public class RateLimiterService {

    private final int limitPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(AppProperties props) {
        this.limitPerMinute = props.rateLimitPerMinute();
    }

    /**
     * @return true if the request is within the limit and the token was consumed.
     */
    public boolean tryConsume(String key) {
        return buckets.computeIfAbsent(key, k -> newBucket()).tryConsume(1);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(limitPerMinute)
                .refillGreedy(limitPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
