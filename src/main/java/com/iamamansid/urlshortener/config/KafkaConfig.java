package com.iamamansid.urlshortener.config;

import com.iamamansid.urlshortener.dto.ClickEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring. The whole configuration (producer, consumer, topic) is
 * skipped when {@code app.kafka-enabled=false}, so the app boots cleanly
 * with no broker on the network. SASL/PLAIN (Redpanda Cloud, Upstash, etc.)
 * is enabled only when {@code KAFKA_SASL_JAAS_CONFIG} is set; otherwise plain
 * PLAINTEXT is used (local docker-compose).
 */
@Configuration
@ConditionalOnProperty(prefix = "app", name = "kafka-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${KAFKA_SASL_JAAS_CONFIG:}")
    private String saslJaasConfig;

    @Value("${KAFKA_SASL_MECHANISM:PLAIN}")
    private String saslMechanism;

    private final AppProperties props;

    public KafkaConfig(AppProperties props) {
        this.props = props;
    }

    private Map<String, Object> commonProps() {
        Map<String, Object> p = new HashMap<>();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        if (saslJaasConfig != null && !saslJaasConfig.isBlank()) {
            p.put("security.protocol", "SASL_SSL");
            p.put("sasl.mechanism", saslMechanism);
            p.put("sasl.jaas.config", saslJaasConfig);
        }
        return p;
    }

    // ---------------------------------------------------------- producer

    @Bean
    public ProducerFactory<String, ClickEvent> clickEventProducerFactory() {
        Map<String, Object> p = commonProps();
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        p.put(ProducerConfig.ACKS_CONFIG, "1");
        p.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        return new DefaultKafkaProducerFactory<>(p);
    }

    @Bean
    public KafkaTemplate<String, ClickEvent> clickEventKafkaTemplate(
            ProducerFactory<String, ClickEvent> clickEventProducerFactory) {
        return new KafkaTemplate<>(clickEventProducerFactory);
    }

    // ---------------------------------------------------------- consumer

    @Bean
    public ConsumerFactory<String, ClickEvent> clickEventConsumerFactory() {
        Map<String, Object> p = commonProps();
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "click-aggregator");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        p.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        p.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ClickEvent.class.getName());
        p.put(JsonDeserializer.TRUSTED_PACKAGES, "com.iamamansid.urlshortener.dto");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return new DefaultKafkaConsumerFactory<>(p);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ClickEvent> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, ClickEvent> clickEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ClickEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(clickEventConsumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }

    // ---------------------------------------------------------- topic

    @Bean
    public org.apache.kafka.clients.admin.NewTopic clickTopic() {
        return TopicBuilder.name(props.clickTopic())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
