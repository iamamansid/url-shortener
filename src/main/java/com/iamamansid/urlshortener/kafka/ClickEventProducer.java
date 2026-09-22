package com.iamamansid.urlshortener.kafka;

import com.iamamansid.urlshortener.config.AppProperties;
import com.iamamansid.urlshortener.dto.ClickEvent;
import com.iamamansid.urlshortener.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

/**
 * Publishes click events. Kafka itself is optional: when
 * {@code app.kafka-enabled} is {@code false} (env {@code KAFKA_ENABLED=false}),
 * there is no broker to talk to, so clicks are counted with a direct atomic
 * DB update instead of going through the Kafka pipeline. Stats stay accurate
 * in both modes; only the async aggregation step is skipped.
 *
 * <p>The template is injected via {@link ObjectProvider} so this bean can be
 * constructed even when the Kafka configuration is disabled.
 */
@Service
public class ClickEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventProducer.class);

    private final ObjectProvider<KafkaTemplate<String, ClickEvent>> kafkaTemplate;
    private final ShortUrlRepository repository;
    private final TransactionTemplate txTemplate;
    private final AppProperties props;

    public ClickEventProducer(ObjectProvider<KafkaTemplate<String, ClickEvent>> kafkaTemplate,
                              ShortUrlRepository repository,
                              TransactionTemplate txTemplate,
                              AppProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.repository = repository;
        this.txTemplate = txTemplate;
        this.props = props;
    }

    public void publishClick(String code) {
        if (props.kafkaEnabled()) {
            publishToKafka(code);
        } else {
            countDirectly(code);
        }
    }

    private void publishToKafka(String code) {
        KafkaTemplate<String, ClickEvent> template = Objects.requireNonNull(
                kafkaTemplate.getIfAvailable(),
                "KAFKA_ENABLED=true but no KafkaTemplate bean exists — check KafkaConfig");
        ClickEvent event = new ClickEvent(code, Instant.now());
        // Keyed by code so all clicks for one link land on the same partition (ordering).
        // send() is non-blocking — the redirect path never waits for Kafka.
        template.send(props.clickTopic(), code, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish click event for code '{}'", code, ex);
            } else if (log.isDebugEnabled()) {
                log.debug("Published click event for code '{}' to partition {}",
                        code, result.getRecordMetadata().partition());
            }
        });
    }

    /**
     * No-Kafka fallback: one indexed, atomic {@code UPDATE ... SET clicks =
     * clicks + 1} — the same statement the Kafka consumer uses. It runs on the
     * redirect thread, but it's a single indexed write (typically sub-ms).
     */
    private void countDirectly(String code) {
        txTemplate.executeWithoutResult(status -> {
            int updated = repository.incrementClicks(code, 1L, Instant.now());
            if (updated == 0) {
                // Code was deleted after the click — safe to drop.
                log.warn("Click for unknown code '{}' dropped (kafka disabled)", code);
            }
        });
    }
}
