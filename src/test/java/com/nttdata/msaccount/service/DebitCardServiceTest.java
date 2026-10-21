package com.nttdata.msaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.nttdata.msaccount.exception.BusinessException;
import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerType;
import com.nttdata.msaccount.model.DebitCardStatus;
import com.nttdata.msaccount.model.IssuedDebitCard;
import com.nttdata.msaccount.repository.AccountRepository;
import com.nttdata.msaccount.repository.IssuedDebitCardRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DebitCardServiceTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final String CUSTOMER_ID = "cust-1";

    @Mock
    private AccountRepository accounts;

    @Mock
    private IssuedDebitCardRepository cards;

    private DebitCardService service;

    @BeforeEach
    void setUp() {
        service = new DebitCardService(accounts, cards);
    }

    @Test
    void issueCreatesActiveCardForActiveAccount() {
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Mono.just(account(AccountStatus.ACTIVE)));
        when(cards.save(any(IssuedDebitCard.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        IssuedDebitCard issued = service.issue(ACCOUNT_ID).blockingGet();

        assertThat(issued.getId()).isNotBlank();
        assertThat(issued.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(issued.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(issued.getStatus()).isEqualTo(DebitCardStatus.ACTIVE);
        assertThat(issued.getCreatedAt()).isNotNull();
    }

    @Test
    void issueRejectsInactiveAccountWithConflict() {
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Mono.just(account(AccountStatus.INACTIVE)));

        service.issue(ACCOUNT_ID).test()
                .assertError(error -> error instanceof BusinessException
                        && ((BusinessException) error).getStatus() == HttpStatus.CONFLICT);
    }

    @Test
    void issueFailsWithNotFoundWhenAccountIsMissing() {
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Mono.empty());

        service.issue(ACCOUNT_ID).test()
                .assertError(error -> error instanceof BusinessException
                        && ((BusinessException) error).getStatus() == HttpStatus.NOT_FOUND);
    }

    @Test
    void listReturnsCardsOfExistingAccount() {
        IssuedDebitCard first = card("card-1", DebitCardStatus.ACTIVE);
        IssuedDebitCard second = card("card-2", DebitCardStatus.ACTIVE);
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Mono.just(account(AccountStatus.ACTIVE)));
        when(cards.findByAccountId(ACCOUNT_ID)).thenReturn(Flux.just(first, second));

        assertThat(service.list(ACCOUNT_ID).blockingGet())
                .containsExactly(first, second);
    }

    @Test
    void listFailsWithNotFoundWhenAccountIsMissing() {
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Mono.empty());

        service.list(ACCOUNT_ID).test()
                .assertError(error -> error instanceof BusinessException
                        && ((BusinessException) error).getStatus() == HttpStatus.NOT_FOUND);
    }

    @Test
    void findActiveReturnsActiveCard() {
        IssuedDebitCard card = card("card-1", DebitCardStatus.ACTIVE);
        when(cards.findById("card-1")).thenReturn(Mono.just(card));

        assertThat(service.findActive("card-1").blockingGet()).isSameAs(card);
    }

    @Test
    void findActiveIgnoresCardWithoutActiveStatus() {
        when(cards.findById("card-1")).thenReturn(Mono.just(card("card-1", null)));

        assertThat(service.findActive("card-1").isEmpty().blockingGet()).isTrue();
    }

    @Test
    void findActiveIsEmptyWhenCardIsMissing() {
        when(cards.findById("missing")).thenReturn(Mono.empty());

        assertThat(service.findActive("missing").isEmpty().blockingGet()).isTrue();
    }

    private static Account account(AccountStatus status) {
        Instant now = Instant.now();
        return Account.builder()
                .id(ACCOUNT_ID)
                .customerId(CUSTOMER_ID)
                .customerType(CustomerType.PERSONAL)
                .accountType(AccountType.SAVINGS)
                .status(status)
                .balance(BigDecimal.TEN)
                .holders(new ArrayList<>())
                .signers(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static IssuedDebitCard card(String id, DebitCardStatus status) {
        return IssuedDebitCard.builder()
                .id(id)
                .accountId(ACCOUNT_ID)
                .customerId(CUSTOMER_ID)
                .status(status)
                .createdAt(Instant.now())
                .build();
    }
}
