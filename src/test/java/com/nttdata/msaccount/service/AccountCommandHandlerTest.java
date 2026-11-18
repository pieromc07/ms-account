package com.nttdata.msaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nttdata.msaccount.event.AccountEventPublisher;
import com.nttdata.msaccount.event.BalanceCommand;
import com.nttdata.msaccount.event.BalanceResult;
import com.nttdata.msaccount.event.DebitCardCharge;
import com.nttdata.msaccount.model.DebitCardStatus;
import com.nttdata.msaccount.model.IssuedDebitCard;
import com.nttdata.msaccount.model.ProcessedBalanceCommand;
import com.nttdata.msaccount.repository.ProcessedBalanceCommandRepository;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccountCommandHandlerTest {

    @Mock
    private BalanceLedger ledger;

    @Mock
    private DebitCardService debitCards;

    @Mock
    private ProcessedBalanceCommandRepository processed;

    @Mock
    private AccountEventPublisher publisher;

    private AccountCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AccountCommandHandler(ledger, debitCards, processed, publisher);
    }

    @Test
    void firstBalanceCommandMovesBalanceStoresOutcomeAndPublishesReply() {
        BalanceCommand command = balanceCommand("tx-1", "acc-1", BalanceCommand.Operation.DEPOSIT, "10.00");
        when(processed.findById("tx-1")).thenReturn(Mono.empty());
        when(ledger.move("acc-1", BalanceCommand.Operation.DEPOSIT, new BigDecimal("10.00"),
                command.occurredAt()))
                .thenReturn(Single.just(Movement.ok("acc-1")));
        when(processed.save(any(ProcessedBalanceCommand.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(publisher.publish(eq(BalanceResult.TOPIC), any(), any())).thenReturn(Completable.complete());

        handler.handleBalance(command).test().assertComplete().assertNoErrors();

        ArgumentCaptor<ProcessedBalanceCommand> saved = ArgumentCaptor.forClass(ProcessedBalanceCommand.class);
        verify(processed).save(saved.capture());
        assertThat(saved.getValue().getTransactionId()).isEqualTo("tx-1");
        assertThat(saved.getValue().getOutcome()).isEqualTo(BalanceResult.Outcome.OK);
        assertThat(saved.getValue().getProcessedAt()).isNotNull();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(BalanceResult.TOPIC), eq("acc-1"), payload.capture());
        BalanceResult reply = (BalanceResult) payload.getValue();
        assertThat(reply.transactionId()).isEqualTo("tx-1");
        assertThat(reply.outcome()).isEqualTo(BalanceResult.Outcome.OK);
    }

    @Test
    void redeliveredCommandRepublishesStoredOutcomeWithoutMovingBalance() {
        ProcessedBalanceCommand stored = ProcessedBalanceCommand.builder()
                .transactionId("tx-1")
                .accountId("acc-1")
                .outcome(BalanceResult.Outcome.REJECTED)
                .reason("insufficient balance")
                .processedAt(Instant.parse("2025-04-01T10:00:00Z"))
                .build();
        when(processed.findById("tx-1")).thenReturn(Mono.just(stored));
        when(publisher.publish(eq(BalanceResult.TOPIC), any(), any())).thenReturn(Completable.complete());

        handler.handleBalance(balanceCommand("tx-1", "acc-1", BalanceCommand.Operation.WITHDRAWAL, "99.00"))
                .test().assertComplete();

        verifyNoInteractions(ledger);
        verify(processed, never()).save(any());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(BalanceResult.TOPIC), eq("acc-1"), payload.capture());
        BalanceResult reply = (BalanceResult) payload.getValue();
        assertThat(reply.outcome()).isEqualTo(BalanceResult.Outcome.REJECTED);
        assertThat(reply.reason()).isEqualTo("insufficient balance");
    }

    @Test
    void ledgerFailureIsPropagatedAndNothingIsStoredOrPublished() {
        when(processed.findById("tx-1")).thenReturn(Mono.empty());
        when(ledger.move(any(), any(), any(), any()))
                .thenReturn(Single.error(new IllegalStateException("db down")));

        handler.handleBalance(balanceCommand("tx-1", "acc-1", BalanceCommand.Operation.DEPOSIT, "5.00"))
                .test()
                .assertError(IllegalStateException.class);

        verify(processed, never()).save(any());
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void debitChargeWithActiveCardWithdrawsFromLinkedAccount() {
        DebitCardCharge charge = debitCharge("tx-9", "card-1", "25.00");
        when(processed.findById("tx-9")).thenReturn(Mono.empty());
        when(debitCards.findActive("card-1")).thenReturn(Maybe.just(activeCard("card-1", "acc-7")));
        when(ledger.move("acc-7", BalanceCommand.Operation.WITHDRAWAL, new BigDecimal("25.00"),
                charge.occurredAt()))
                .thenReturn(Single.just(Movement.ok("acc-7")));
        when(processed.save(any(ProcessedBalanceCommand.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(publisher.publish(eq(DebitCardCharge.RESULT_TOPIC), any(), any())).thenReturn(Completable.complete());

        handler.handleDebitCharge(charge).test().assertComplete();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(DebitCardCharge.RESULT_TOPIC), eq("acc-7"), payload.capture());
        assertThat(((BalanceResult) payload.getValue()).outcome()).isEqualTo(BalanceResult.Outcome.OK);
    }

    @Test
    void debitChargeWithInactiveCardIsRejectedWithoutMovingBalance() {
        when(processed.findById("tx-9")).thenReturn(Mono.empty());
        when(debitCards.findActive("card-1")).thenReturn(Maybe.empty());
        when(processed.save(any(ProcessedBalanceCommand.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(publisher.publish(eq(DebitCardCharge.RESULT_TOPIC), any(), any())).thenReturn(Completable.complete());

        handler.handleDebitCharge(debitCharge("tx-9", "card-1", "25.00")).test().assertComplete();

        verify(ledger, never()).move(any(), any(), any(), any());
        ArgumentCaptor<ProcessedBalanceCommand> saved = ArgumentCaptor.forClass(ProcessedBalanceCommand.class);
        verify(processed).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo(BalanceResult.Outcome.REJECTED);
        assertThat(saved.getValue().getReason()).isEqualTo("debit card not found or inactive");
        assertThat(saved.getValue().getAccountId()).isNull();
    }

    private static BalanceCommand balanceCommand(String txId, String accountId,
            BalanceCommand.Operation operation, String amount) {
        return new BalanceCommand(txId, accountId, operation, new BigDecimal(amount), Instant.now());
    }

    private static DebitCardCharge debitCharge(String txId, String cardId, String amount) {
        return new DebitCardCharge(DebitCardCharge.SCHEMA_VERSION, txId, cardId, new BigDecimal(amount),
                Instant.now());
    }

    private static IssuedDebitCard activeCard(String cardId, String accountId) {
        return IssuedDebitCard.builder()
                .id(cardId)
                .accountId(accountId)
                .customerId("cust-1")
                .status(DebitCardStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }
}
