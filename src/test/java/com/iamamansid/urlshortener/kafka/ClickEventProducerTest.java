package com.iamamansid.urlshortener.kafka;

import com.iamamansid.urlshortener.config.AppProperties;
import com.iamamansid.urlshortener.dto.ClickEvent;
import com.iamamansid.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickEventProducerTest {

    @Mock
    private ObjectProvider<KafkaTemplate<String, ClickEvent>> templateProvider;

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private TransactionTemplate txTemplate;

    private static AppProperties props(boolean kafkaEnabled) {
        return new AppProperties("http://localhost:8080", 20, 24, "url-clicks", 6, kafkaEnabled);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishClick_kafkaEnabled_sendsEventToTopic() {
        KafkaTemplate<String, ClickEvent> template = mock(KafkaTemplate.class);
        when(templateProvider.getIfAvailable()).thenReturn(template);
        SendResult<String, ClickEvent> result = mock(SendResult.class);
        when(template.send(anyString(), anyString(), any(ClickEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(result));

        new ClickEventProducer(templateProvider, repository, txTemplate, props(true))
                .publishClick("abc123");

        ArgumentCaptor<ClickEvent> eventCaptor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(template).send(eq("url-clicks"), eq("abc123"), eventCaptor.capture());
        // No direct DB write in Kafka mode.
        verify(txTemplate, never()).executeWithoutResult(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishClick_kafkaDisabled_countsClickDirectly() {
        // Run the transactional callback inline, like a real TransactionTemplate would.
        doAnswer(inv -> {
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(txTemplate).executeWithoutResult(any(Consumer.class));
        when(repository.incrementClicks(anyString(), eq(1L), any(Instant.class))).thenReturn(1);

        new ClickEventProducer(templateProvider, repository, txTemplate, props(false))
                .publishClick("abc123");

        verify(repository).incrementClicks(eq("abc123"), eq(1L), any(Instant.class));
        // No Kafka interaction at all — the provider must not even be consulted.
        verify(templateProvider, never()).getIfAvailable();
    }
}
