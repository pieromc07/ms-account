package com.nttdata.msaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import com.mongodb.client.result.UpdateResult;
import com.nttdata.msaccount.event.BalanceCommand;
import com.nttdata.msaccount.event.BalanceResult;
import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerProfile;
import com.nttdata.msaccount.repository.AccountRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class BalanceLedgerTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private ReactiveMongoOperations mongo;

    private BalanceLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new BalanceLedger(accounts, mongo);
    }

    @Test
    void nullAmountIsRejectedWithoutTouchingStorage() {
        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.DEPOSIT, null).blockingGet();

        assertRejected(movement, "acc-1", "amount must be positive");
        verifyNoInteractions(accounts, mongo);
    }

    @Test
    void nonPositiveAmountIsRejectedWithoutTouchingStorage() {
        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.WITHDRAWAL, BigDecimal.ZERO).blockingGet();

        assertRejected(movement, "acc-1", "amount must be positive");
        verifyNoInteractions(accounts, mongo);
    }

    @Test
    void unknownAccountIsRejected() {
        when(accounts.findById("acc-1")).thenReturn(Mono.empty());

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.DEPOSIT, new BigDecimal("10.00"))
                .blockingGet();

        assertRejected(movement, "acc-1", "account not found");
        verifyNoInteractions(mongo);
    }

    @Test
    void inactiveAccountIsRejected() {
        when(accounts.findById("acc-1")).thenReturn(Mono.just(account(AccountStatus.INACTIVE,
                CustomerProfile.STANDARD, AccountType.SAVINGS, "1000.00")));

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.DEPOSIT, new BigDecimal("10.00"))
                .blockingGet();

        assertRejected(movement, "acc-1", "account is not active");
        verifyNoInteractions(mongo);
    }

    @Test
    void depositIncreasesBalanceWithCompareAndSet() {
        when(accounts.findById("acc-1")).thenReturn(Mono.just(account(AccountStatus.ACTIVE,
                CustomerProfile.STANDARD, AccountType.SAVINGS, "1000.00")));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Account.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.DEPOSIT, new BigDecimal("150.00"))
                .blockingGet();

        assertThat(movement.outcome()).isEqualTo(BalanceResult.Outcome.OK);
        assertThat(setBalance()).isEqualByComparingTo(new BigDecimal("1150.00"));
    }

    @Test
    void withdrawalDecreasesBalance() {
        when(accounts.findById("acc-1")).thenReturn(Mono.just(account(AccountStatus.ACTIVE,
                CustomerProfile.STANDARD, AccountType.CURRENT, "1000.00")));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Account.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.WITHDRAWAL, new BigDecimal("400.00"))
                .blockingGet();

        assertThat(movement.outcome()).isEqualTo(BalanceResult.Outcome.OK);
        assertThat(setBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    @Test
    void withdrawalAboveBalanceIsRejected() {
        when(accounts.findById("acc-1")).thenReturn(Mono.just(account(AccountStatus.ACTIVE,
                CustomerProfile.STANDARD, AccountType.CURRENT, "50.00")));

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.WITHDRAWAL, new BigDecimal("60.00"))
                .blockingGet();

        assertRejected(movement, "acc-1", "insufficient balance");
        verifyNoInteractions(mongo);
    }

    @Test
    void vipSavingsCannotFallBelowMinimum() {
        when(accounts.findById("acc-1")).thenReturn(Mono.just(account(AccountStatus.ACTIVE,
                CustomerProfile.VIP, AccountType.SAVINGS, "2500.00")));

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.WITHDRAWAL, new BigDecimal("600.00"))
                .blockingGet();

        assertRejected(movement, "acc-1", "VIP savings account cannot fall below S/ 2,000.00");
        verifyNoInteractions(mongo);
    }

    @Test
    void vipSavingsCanWithdrawDownToMinimum() {
        when(accounts.findById("acc-1")).thenReturn(Mono.just(account(AccountStatus.ACTIVE,
                CustomerProfile.VIP, AccountType.SAVINGS, "2500.00")));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Account.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.WITHDRAWAL, new BigDecimal("500.00"))
                .blockingGet();

        assertThat(movement.outcome()).isEqualTo(BalanceResult.Outcome.OK);
        assertThat(setBalance()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    void standardSavingsIsNotSubjectToVipMinimum() {
        when(accounts.findById("acc-1")).thenReturn(Mono.just(account(AccountStatus.ACTIVE,
                CustomerProfile.STANDARD, AccountType.SAVINGS, "1000.00")));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Account.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.WITHDRAWAL, new BigDecimal("900.00"))
                .blockingGet();

        assertThat(movement.outcome()).isEqualTo(BalanceResult.Outcome.OK);
        assertThat(setBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void concurrentChangeIsRetriedWithFreshBalance() {
        when(accounts.findById("acc-1")).thenReturn(
                Mono.just(account(AccountStatus.ACTIVE, CustomerProfile.STANDARD, AccountType.CURRENT, "100.00")),
                Mono.just(account(AccountStatus.ACTIVE, CustomerProfile.STANDARD, AccountType.CURRENT, "200.00")));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Account.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)),
                        Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Movement movement = ledger.move("acc-1", BalanceCommand.Operation.DEPOSIT, new BigDecimal("50.00"))
                .blockingGet();

        assertThat(movement.outcome()).isEqualTo(BalanceResult.Outcome.OK);
        verify(accounts, times(2)).findById("acc-1");
        assertThat(setBalance()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void persistentConcurrentChangeFailsAfterMaxAttempts() {
        when(accounts.findById("acc-1")).thenReturn(Mono.just(account(AccountStatus.ACTIVE,
                CustomerProfile.STANDARD, AccountType.CURRENT, "100.00")));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Account.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        assertThrows(IllegalStateException.class, () -> ledger.move("acc-1",
                BalanceCommand.Operation.DEPOSIT, new BigDecimal("50.00")).blockingGet());

        verify(accounts, times(BalanceLedger.MAX_ATTEMPTS)).findById("acc-1");
    }

    private BigDecimal setBalance() {
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongo, atLeast(1)).updateFirst(any(Query.class), captor.capture(), eq(Account.class));
        List<Update> updates = captor.getAllValues();
        Document set = (Document) updates.get(updates.size() - 1).getUpdateObject().get("$set");
        return (BigDecimal) set.get("balance");
    }


    private static void assertRejected(Movement movement, String accountId, String reason) {
        assertThat(movement.outcome()).isEqualTo(BalanceResult.Outcome.REJECTED);
        assertThat(movement.accountId()).isEqualTo(accountId);
        assertThat(movement.reason()).isEqualTo(reason);
    }

    private static Account account(AccountStatus status, CustomerProfile profile, AccountType type, String balance) {
        return Account.builder()
                .id("acc-1")
                .customerId("cust-1")
                .status(status)
                .customerProfile(profile)
                .accountType(type)
                .balance(new BigDecimal(balance))
                .build();
    }
}
