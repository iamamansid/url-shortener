package com.iamamansid.urlshortener.kafka;

import com.iamamansid.urlshortener.dto.ClickEvent;
import com.iamamansid.urlshortener.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    public ClickEventConsumer(ShortUrlRepository repository) {
        this.repository = repository;
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
        Map<String, Long> countsByCode = events.stream()
                .filter(e -> e != null && e.code() != null && !e.code().isBlank())
                .collect(Collectors.groupingBy(ClickEvent::code, Collectors.counting()));

        Instant now = Instant.now();
        countsByCode.forEach((code, count) -> {
            int updated = repository.incrementClicks(code, count, now);
            if (updated == 0) {
                // Code was deleted after the click — safe to drop.
                log.warn("Received click event(s) for unknown code '{}', skipping", code);
            }
        });
        log.debug("Aggregated {} click events across {} codes", events.size(), countsByCode.size());
    }
}
