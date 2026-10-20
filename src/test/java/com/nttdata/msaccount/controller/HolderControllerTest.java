package com.nttdata.msaccount.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.nttdata.msaccount.exception.BusinessException;
import com.nttdata.msaccount.generated.dto.AddAccountMemberRequest;
import com.nttdata.msaccount.mapper.AccountMapper;
import com.nttdata.msaccount.model.Member;
import com.nttdata.msaccount.service.AccountService;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class HolderControllerTest {

    private static final String ACCOUNT_ID = "64b000000000000000000001";
    private static final String CUSTOMER_ID = "64b000000000000000000002";

    @Mock
    private AccountService service;

    private HolderController controller;

    @BeforeEach
    void setUp() {
        controller = new HolderController(service, new AccountMapper());
    }

    @Test
    void addHolderReturnsCreatedWithBodyAndNoLocationHeader() {
        Instant addedAt = Instant.parse("2025-03-01T09:00:00Z");
        when(service.addHolder(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(Single.just(new Member(CUSTOMER_ID, addedAt)));

        StepVerifier.create(controller.addAccountHolder(ACCOUNT_ID,
                        Mono.just(new AddAccountMemberRequest().customerId(CUSTOMER_ID)), null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getHeaders().getLocation()).isNull();
                    assertThat(response.getBody().getCustomerId()).isEqualTo(CUSTOMER_ID);
                    assertThat(response.getBody().getAddedAt().toInstant()).isEqualTo(addedAt);
                })
                .verifyComplete();
    }

    @Test
    void addHolderPropagatesConflict() {
        when(service.addHolder(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Single.error(new BusinessException(HttpStatus.CONFLICT, "duplicate")));

        StepVerifier.create(controller.addAccountHolder(ACCOUNT_ID,
                        Mono.just(new AddAccountMemberRequest().customerId(CUSTOMER_ID)), null))
                .expectErrorMatches(error -> error instanceof BusinessException
                        && ((BusinessException) error).getStatus() == HttpStatus.CONFLICT)
                .verify();
    }

    @Test
    void listHoldersReturnsOkWithMembers() {
        Member member = new Member(CUSTOMER_ID, Instant.parse("2025-03-01T09:00:00Z"));
        when(service.listHolders(ACCOUNT_ID)).thenReturn(Single.just(List.of(member)));

        StepVerifier.create(controller.listAccountHolders(ACCOUNT_ID, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().collectList().block())
                            .extracting(dto -> dto.getCustomerId())
                            .containsExactly(CUSTOMER_ID);
                })
                .verifyComplete();
    }

    @Test
    void removeHolderReturnsNoContent() {
        when(service.removeHolder(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(Completable.complete());

        StepVerifier.create(controller.removeAccountHolder(ACCOUNT_ID, CUSTOMER_ID, null))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                .verifyComplete();
        verify(service).removeHolder(ACCOUNT_ID, CUSTOMER_ID);
    }

    @Test
    void removeHolderPropagatesNotFound() {
        when(service.removeHolder(any(), any()))
                .thenReturn(Completable.error(new BusinessException(HttpStatus.NOT_FOUND, "missing")));

        StepVerifier.create(controller.removeAccountHolder(ACCOUNT_ID, CUSTOMER_ID, null))
                .expectError(BusinessException.class)
                .verify();
    }
}
