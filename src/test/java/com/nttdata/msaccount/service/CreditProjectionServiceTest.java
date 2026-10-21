package com.nttdata.msaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nttdata.msaccount.event.CreditCardEvent;
import com.nttdata.msaccount.event.OverdueDebtEvent;
import com.nttdata.msaccount.model.CreditCardProjection;
import com.nttdata.msaccount.model.OverdueDebtProjection;
import com.nttdata.msaccount.repository.CreditCardProjectionRepository;
import com.nttdata.msaccount.repository.OverdueDebtProjectionRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CreditProjectionServiceTest {

    private static final Instant T1 = Instant.parse("2025-05-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2025-05-02T10:00:00Z");

    @Mock
    private CreditCardProjectionRepository cardProjections;

    @Mock
    private OverdueDebtProjectionRepository overdueProjections;

    private CreditProjectionService service;

    @BeforeEach
    void setUp() {
        service = new CreditProjectionService(cardProjections, overdueProjections);
    }

    @Test
    void hasActiveCardDelegatesToRepository() {
        when(cardProjections.existsByCustomerIdAndActiveTrue("cust-1")).thenReturn(Mono.just(true));

        assertThat(service.hasActiveCard("cust-1").blockingGet()).isTrue();
    }

    @Test
    void isOverdueIsFalseWhenNoProjectionExists() {
        when(overdueProjections.findById("cust-1")).thenReturn(Mono.empty());

        assertThat(service.isOverdue("cust-1").blockingGet()).isFalse();
    }

    @Test
    void isOverdueReflectsStoredProjection() {
        when(overdueProjections.findById("cust-1"))
                .thenReturn(Mono.just(new OverdueDebtProjection("cust-1", true, T1)));

        assertThat(service.isOverdue("cust-1").blockingGet()).isTrue();
    }

    @Test
    void createdCardEventStoresActiveProjection() {
        when(cardProjections.findById("card-1")).thenReturn(Mono.empty());
        when(cardProjections.save(any(CreditCardProjection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.applyCardEvent(cardEvent("card-1", CreditCardEvent.EventType.CREATED, T1))
                .test().assertComplete();

        CreditCardProjection saved = captureCard();
        assertThat(saved.getCreditCardId()).isEqualTo("card-1");
        assertThat(saved.getCustomerId()).isEqualTo("cust-1");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getOccurredAt()).isEqualTo(T1);
    }

    @Test
    void cancelledCardEventMarksExistingProjectionInactive() {
        when(cardProjections.findById("card-1"))
                .thenReturn(Mono.just(new CreditCardProjection("card-1", "cust-1", true, T1)));
        when(cardProjections.save(any(CreditCardProjection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.applyCardEvent(cardEvent("card-1", CreditCardEvent.EventType.CANCELLED, T2))
                .test().assertComplete();

        CreditCardProjection saved = captureCard();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getOccurredAt()).isEqualTo(T2);
    }

    @Test
    void staleCardEventIsIgnored() {
        when(cardProjections.findById("card-1"))
                .thenReturn(Mono.just(new CreditCardProjection("card-1", "cust-1", false, T2)));

        service.applyCardEvent(cardEvent("card-1", CreditCardEvent.EventType.CREATED, T1))
                .test().assertComplete();

        verify(cardProjections, never()).save(any());
    }

    @Test
    void overdueDetectedEventStoresOverdueFlag() {
        when(overdueProjections.findById("cust-1")).thenReturn(Mono.empty());
        when(overdueProjections.save(any(OverdueDebtProjection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.applyOverdueEvent(overdueEvent(OverdueDebtEvent.EventType.OVERDUE_DEBT_DETECTED, T1))
                .test().assertComplete();

        ArgumentCaptor<OverdueDebtProjection> saved = ArgumentCaptor.forClass(OverdueDebtProjection.class);
        verify(overdueProjections).save(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo("cust-1");
        assertThat(saved.getValue().isOverdue()).isTrue();
    }

    @Test
    void overdueClearedEventClearsOverdueFlag() {
        when(overdueProjections.findById("cust-1"))
                .thenReturn(Mono.just(new OverdueDebtProjection("cust-1", true, T1)));
        when(overdueProjections.save(any(OverdueDebtProjection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.applyOverdueEvent(overdueEvent(OverdueDebtEvent.EventType.OVERDUE_DEBT_CLEARED, T2))
                .test().assertComplete();

        ArgumentCaptor<OverdueDebtProjection> saved = ArgumentCaptor.forClass(OverdueDebtProjection.class);
        verify(overdueProjections).save(saved.capture());
        assertThat(saved.getValue().isOverdue()).isFalse();
        assertThat(saved.getValue().getOccurredAt()).isEqualTo(T2);
    }

    @Test
    void staleOverdueEventIsIgnored() {
        when(overdueProjections.findById("cust-1"))
                .thenReturn(Mono.just(new OverdueDebtProjection("cust-1", false, T2)));

        service.applyOverdueEvent(overdueEvent(OverdueDebtEvent.EventType.OVERDUE_DEBT_DETECTED, T1))
                .test().assertComplete();

        verify(overdueProjections, never()).save(any());
    }

    private CreditCardProjection captureCard() {
        ArgumentCaptor<CreditCardProjection> saved = ArgumentCaptor.forClass(CreditCardProjection.class);
        verify(cardProjections).save(saved.capture());
        return saved.getValue();
    }

    private static CreditCardEvent cardEvent(String cardId, CreditCardEvent.EventType type, Instant at) {
        return new CreditCardEvent(CreditCardEvent.SCHEMA_VERSION, cardId, "cust-1", type, at);
    }

    private static OverdueDebtEvent overdueEvent(OverdueDebtEvent.EventType type, Instant at) {
        return new OverdueDebtEvent(OverdueDebtEvent.SCHEMA_VERSION, "cust-1", type, at);
    }
}
