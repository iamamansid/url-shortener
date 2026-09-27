package com.iamamansid.urlshortener.kafka;

import com.iamamansid.urlshortener.dto.ClickEvent;
import com.iamamansid.urlshortener.entity.LinkClick;
import com.iamamansid.urlshortener.entity.ShortUrl;
import com.iamamansid.urlshortener.repository.LinkClickRepository;
import com.iamamansid.urlshortener.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Consumer group {@code click-aggregator}. Consumes click events in batches,
 * aggregates counts per code in memory, and applies a single atomic
 * {@code UPDATE ... SET clicks = clicks + n} per code — no read-modify-write,
 * so concurrent batches never lose increments. Reprocessing a batch (at-least-
 * once delivery) only over-counts if the same batch is redelivered; grouping
 * keeps the window small.
 */
@Service
@ConditionalOnProperty(prefix = "app", name = "kafka-enabled", havingValue = "true", matchIfMissing = true)
public class ClickEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventConsumer.class);

    private final ShortUrlRepository repository;
    private final LinkClickRepository clickRepository;

    public ClickEventConsumer(ShortUrlRepository repository, LinkClickRepository clickRepository) {
        this.repository = repository;
        this.clickRepository = clickRepository;
    }

    @KafkaListener(
            topics = "${app.click-topic}",
            groupId = "click-aggregator",
            containerFactory = "batchKafkaListenerContainerFactory")
    @Transactional
    public void consume(List<ClickEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        Map<String, List<ClickEvent>> eventsByCode = events.stream()
                .filter(e -> e != null && e.code() != null && !e.code().isBlank())
                .collect(Collectors.groupingBy(ClickEvent::code));

        Instant now = Instant.now();
        eventsByCode.forEach((code, codeEvents) -> {
            int updated = repository.incrementClicks(code, codeEvents.size(), now);
            if (updated == 0) {
                // Code was deleted after the click — safe to drop.
                log.warn("Received click event(s) for unknown code '{}', skipping", code);
                return;
            }
            // Historical rows for the per-day click chart in the admin dashboard.
            Optional<ShortUrl> entity = repository.findByCode(code);
            if (entity.isEmpty()) {
                return;
            }
            List<LinkClick> rows = new ArrayList<>(codeEvents.size());
            for (ClickEvent event : codeEvents) {
                LinkClick row = new LinkClick();
                row.setShortUrl(entity.get());
                row.setClickedAt(event.clickedAt() != null ? event.clickedAt() : now);
                rows.add(row);
            }
            clickRepository.saveAll(rows);
        });
        log.debug("Aggregated {} click events across {} codes", events.size(), eventsByCode.size());
    }
}
