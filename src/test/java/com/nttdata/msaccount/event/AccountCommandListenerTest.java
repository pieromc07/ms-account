package com.nttdata.msaccount.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nttdata.msaccount.service.AccountCommandHandler;

import io.reactivex.rxjava3.core.Completable;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AccountCommandListenerTest {

    private static final String BALANCE_PAYLOAD = """
            {"schemaVersion":1,"transactionId":"tx-1","accountId":"acc-1",
             "operation":"DEPOSIT","amount":150.00,"occurredAt":"2026-05-10T10:00:00Z"}
            """;

    private static final String DEBIT_PAYLOAD = """
            {"schemaVersion":1,"transactionId":"tx-2","debitCardId":"card-9",
             "amount":25.50,"occurredAt":"2026-05-10T11:00:00Z"}
            """;

    @Mock
    private AccountCommandHandler handler;

    private AccountCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new AccountCommandListener(handler, JsonMapper.builder().build());
    }

    @Test
    void balancePayloadIsParsedAndDelegated() {
        when(handler.handleBalance(any(BalanceCommand.class))).thenReturn(Completable.complete());

        listener.onBalanceCommand(BALANCE_PAYLOAD);

        ArgumentCaptor<BalanceCommand> command = ArgumentCaptor.forClass(BalanceCommand.class);
        verify(handler).handleBalance(command.capture());
        assertThat(command.getValue().transactionId()).isEqualTo("tx-1");
        assertThat(command.getValue().accountId()).isEqualTo("acc-1");
        assertThat(command.getValue().operation()).isEqualTo(BalanceCommand.Operation.DEPOSIT);
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void debitPayloadIsParsedAndDelegated() {
        when(handler.handleDebitCharge(any(DebitCardCharge.class))).thenReturn(Completable.complete());

        listener.onDebitCardCharge(DEBIT_PAYLOAD);

        ArgumentCaptor<DebitCardCharge> charge = ArgumentCaptor.forClass(DebitCardCharge.class);
        verify(handler).handleDebitCharge(charge.capture());
        assertThat(charge.getValue().transactionId()).isEqualTo("tx-2");
        assertThat(charge.getValue().debitCardId()).isEqualTo("card-9");
        assertThat(charge.getValue().amount()).isEqualByComparingTo(new BigDecimal("25.50"));
    }

    @Test
    void balanceHandlerFailureIsRethrownSoKafkaRedelivers() {
        when(handler.handleBalance(any(BalanceCommand.class)))
                .thenReturn(Completable.error(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> listener.onBalanceCommand(BALANCE_PAYLOAD));
    }

    @Test
    void debitHandlerFailureIsRethrownSoKafkaRedelivers() {
        when(handler.handleDebitCharge(any(DebitCardCharge.class)))
                .thenReturn(Completable.error(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> listener.onDebitCardCharge(DEBIT_PAYLOAD));
    }

    @Test
    void malformedBalancePayloadFailsTheRecord() {
        assertThrows(RuntimeException.class, () -> listener.onBalanceCommand("{not json"));
    }

    @Test
    void malformedDebitPayloadFailsTheRecord() {
        assertThrows(RuntimeException.class, () -> listener.onDebitCardCharge("{not json"));
    }
}
