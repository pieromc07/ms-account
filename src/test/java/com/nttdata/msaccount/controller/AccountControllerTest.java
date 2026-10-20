package com.nttdata.msaccount.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nttdata.msaccount.exception.BusinessException;
import com.nttdata.msaccount.generated.dto.CreateAccountRequest;
import com.nttdata.msaccount.generated.dto.UpdateAccountStatusRequest;
import com.nttdata.msaccount.mapper.AccountMapper;
import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerType;
import com.nttdata.msaccount.service.AccountService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private static final String ACCOUNT_ID = "64b000000000000000000001";
    private static final String CUSTOMER_ID = "64b000000000000000000002";

    @Mock
    private AccountService service;

    private AccountController controller;

    @BeforeEach
    void setUp() {
        controller = new AccountController(service, new AccountMapper());
    }

    private Account account(AccountStatus status) {
        return Account.builder()
                .id(ACCOUNT_ID)
                .customerId(CUSTOMER_ID)
                .customerType(CustomerType.PERSONAL)
                .accountType(AccountType.SAVINGS)
                .status(status)
                .balance(BigDecimal.TEN)
                .holders(new ArrayList<>())
                .signers(new ArrayList<>())
                .build();
    }

    @Test
    void createAccountReturnsCreatedWithLocationAndBody() {
        when(service.open(CUSTOMER_ID, AccountType.SAVINGS, new BigDecimal("100.0")))
                .thenReturn(Single.just(account(AccountStatus.ACTIVE)));
        CreateAccountRequest request = new CreateAccountRequest()
                .customerId(CUSTOMER_ID)
                .accountType(com.nttdata.msaccount.generated.dto.AccountType.SAVINGS)
                .openingAmount(100.0d);

        StepVerifier.create(controller.createAccount(Mono.just(request), null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getHeaders().getLocation().getPath())
                            .isEqualTo("/api/v1/accounts/" + ACCOUNT_ID);
                    assertThat(response.getBody().getId()).isEqualTo(ACCOUNT_ID);
                    assertThat(response.getBody().getStatus())
                            .isEqualTo(com.nttdata.msaccount.generated.dto.AccountStatus.ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void createAccountPropagatesBusinessError() {
        when(service.open(any(), any(), any()))
                .thenReturn(Single.error(new BusinessException(HttpStatus.CONFLICT, "already exists")));
        CreateAccountRequest request = new CreateAccountRequest()
                .customerId(CUSTOMER_ID)
                .accountType(com.nttdata.msaccount.generated.dto.AccountType.CURRENT)
                .openingAmount(0.0d);

        StepVerifier.create(controller.createAccount(Mono.just(request), null))
                .expectErrorMatches(error -> error instanceof BusinessException
                        && ((BusinessException) error).getStatus() == HttpStatus.CONFLICT)
                .verify();
    }

    @Test
    void listAccountsPassesFiltersAndStreamsBody() {
        when(service.list(CUSTOMER_ID, AccountType.CURRENT, AccountStatus.ACTIVE))
                .thenReturn(Single.just(List.of(account(AccountStatus.ACTIVE))));

        StepVerifier.create(controller.listAccounts(CUSTOMER_ID,
                        com.nttdata.msaccount.generated.dto.AccountType.CURRENT,
                        com.nttdata.msaccount.generated.dto.AccountStatus.ACTIVE,
                        null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().collectList().block()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void listAccountsWithoutFiltersPassesNulls() {
        when(service.list(CUSTOMER_ID, null, null)).thenReturn(Single.just(List.of()));

        StepVerifier.create(controller.listAccounts(CUSTOMER_ID, null, null, null))
                .assertNext(response -> assertThat(response.getBody().collectList().block()).isEmpty())
                .verifyComplete();
    }

    @Test
    void getAccountByIdReturnsOk() {
        when(service.findById(ACCOUNT_ID)).thenReturn(Single.just(account(AccountStatus.ACTIVE)));

        StepVerifier.create(controller.getAccountById(ACCOUNT_ID, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getCustomerId()).isEqualTo(CUSTOMER_ID);
                })
                .verifyComplete();
    }

    @Test
    void getAccountByIdPropagatesNotFound() {
        when(service.findById(ACCOUNT_ID))
                .thenReturn(Single.error(new BusinessException(HttpStatus.NOT_FOUND, "missing")));

        StepVerifier.create(controller.getAccountById(ACCOUNT_ID, null))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void updateAccountStatusMapsStatusAndReturnsOk() {
        when(service.updateStatus(ACCOUNT_ID, AccountStatus.INACTIVE))
                .thenReturn(Single.just(account(AccountStatus.INACTIVE)));
        UpdateAccountStatusRequest request = new UpdateAccountStatusRequest()
                .status(com.nttdata.msaccount.generated.dto.AccountStatus.INACTIVE);

        StepVerifier.create(controller.updateAccountStatus(ACCOUNT_ID, Mono.just(request), null))
                .assertNext(response -> assertThat(response.getBody().getStatus())
                        .isEqualTo(com.nttdata.msaccount.generated.dto.AccountStatus.INACTIVE))
                .verifyComplete();
    }

    @Test
    void deleteAccountDeactivatesAndReturnsNoContent() {
        when(service.deactivate(ACCOUNT_ID)).thenReturn(Completable.complete());

        StepVerifier.create(controller.deleteAccount(ACCOUNT_ID, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                    assertThat(response.getBody()).isNull();
                })
                .verifyComplete();
        verify(service).deactivate(ACCOUNT_ID);
    }

    @Test
    void deleteAccountPropagatesError() {
        when(service.deactivate(ACCOUNT_ID))
                .thenReturn(Completable.error(new BusinessException(HttpStatus.NOT_FOUND, "missing")));

        StepVerifier.create(controller.deleteAccount(ACCOUNT_ID, null))
                .expectError(BusinessException.class)
                .verify();
    }
}
