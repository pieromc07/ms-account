package com.nttdata.msaccount.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.nttdata.msaccount.model.AccountType;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AccountEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private AccountEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AccountEventPublisher(kafkaTemplate, JsonMapper.builder().build());
    }

    @Test
    void publishSendsJsonPayloadToTopicWithKey() {
        when(kafkaTemplate.send(eq(AccountOpened.TOPIC), eq("acc-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(AccountOpened.TOPIC, "acc-1", accountOpened()).test().assertComplete();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(AccountOpened.TOPIC), eq("acc-1"), payload.capture());
        assertThat(payload.getValue())
                .contains("\"accountId\":\"acc-1\"")
                .contains("\"accountType\":\"SAVINGS\"")
                .contains("\"schemaVersion\":1");
    }

    @Test
    void publishPropagatesBrokerFailure() {
        when(kafkaTemplate.send(eq(AccountOpened.TOPIC), eq("acc-1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        publisher.publish(AccountOpened.TOPIC, "acc-1", accountOpened())
                .test()
                .assertError(IllegalStateException.class);
    }

    @Test
    void publishDoesNotSendUntilSubscribed() {
        publisher.publish(AccountOpened.TOPIC, "acc-1", accountOpened());

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void unserializablePayloadFailsWithoutSending() {
        publisher.publish(AccountOpened.TOPIC, "acc-1", new FailingGetter())
                .test()
                .assertError(RuntimeException.class);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    public static class FailingGetter {
        public String getBoom() {
            throw new IllegalStateException("cannot serialize");
        }
    }

    private static AccountOpened accountOpened() {
        return new AccountOpened(AccountOpened.SCHEMA_VERSION, "acc-1", "cust-1",
                AccountType.SAVINGS, Instant.parse("2026-05-10T10:00:00Z"));
    }
}
