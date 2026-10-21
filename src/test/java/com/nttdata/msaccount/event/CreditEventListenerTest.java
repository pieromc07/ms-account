package com.nttdata.msaccount.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nttdata.msaccount.service.CreditProjectionService;

import io.reactivex.rxjava3.core.Completable;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CreditEventListenerTest {

    private static final String CARD_PAYLOAD = """
            {"schemaVersion":1,"creditCardId":"card-1","customerId":"cust-1",
             "eventType":"CREATED","occurredAt":"2026-05-10T10:00:00Z"}
            """;

    private static final String OVERDUE_PAYLOAD = """
            {"schemaVersion":1,"customerId":"cust-1",
             "eventType":"OVERDUE_DEBT_DETECTED","occurredAt":"2026-05-10T10:00:00Z"}
            """;

    @Mock
    private CreditProjectionService projections;

    private CreditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new CreditEventListener(projections, JsonMapper.builder().build());
    }

    @Test
    void cardPayloadIsParsedAndDelegated() {
        when(projections.applyCardEvent(any(CreditCardEvent.class))).thenReturn(Completable.complete());

        listener.onCreditCardEvent(CARD_PAYLOAD);

        ArgumentCaptor<CreditCardEvent> event = ArgumentCaptor.forClass(CreditCardEvent.class);
        verify(projections).applyCardEvent(event.capture());
        assertThat(event.getValue().creditCardId()).isEqualTo("card-1");
        assertThat(event.getValue().customerId()).isEqualTo("cust-1");
        assertThat(event.getValue().eventType()).isEqualTo(CreditCardEvent.EventType.CREATED);
    }

    @Test
    void overduePayloadIsParsedAndDelegated() {
        when(projections.applyOverdueEvent(any(OverdueDebtEvent.class))).thenReturn(Completable.complete());

        listener.onOverdueDebtEvent(OVERDUE_PAYLOAD);

        ArgumentCaptor<OverdueDebtEvent> event = ArgumentCaptor.forClass(OverdueDebtEvent.class);
        verify(projections).applyOverdueEvent(event.capture());
        assertThat(event.getValue().customerId()).isEqualTo("cust-1");
        assertThat(event.getValue().eventType()).isEqualTo(OverdueDebtEvent.EventType.OVERDUE_DEBT_DETECTED);
    }

    @Test
    void cardProjectionFailureIsRethrownSoKafkaRedelivers() {
        when(projections.applyCardEvent(any(CreditCardEvent.class)))
                .thenReturn(Completable.error(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> listener.onCreditCardEvent(CARD_PAYLOAD));
    }

    @Test
    void overdueProjectionFailureIsRethrownSoKafkaRedelivers() {
        when(projections.applyOverdueEvent(any(OverdueDebtEvent.class)))
                .thenReturn(Completable.error(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> listener.onOverdueDebtEvent(OVERDUE_PAYLOAD));
    }

    @Test
    void malformedCardPayloadFailsTheRecord() {
        assertThrows(RuntimeException.class, () -> listener.onCreditCardEvent("{not json"));
    }

    @Test
    void malformedOverduePayloadFailsTheRecord() {
        assertThrows(RuntimeException.class, () -> listener.onOverdueDebtEvent("{not json"));
    }
}
