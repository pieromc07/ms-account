package com.nttdata.msaccount.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
class SignerControllerTest {

    private static final String ACCOUNT_ID = "64b000000000000000000001";
    private static final String CUSTOMER_ID = "64b000000000000000000003";

    @Mock
    private AccountService service;

    private SignerController controller;

    @BeforeEach
    void setUp() {
        controller = new SignerController(service, new AccountMapper());
    }

    @Test
    void addSignerReturnsCreatedWithBodyAndNoLocationHeader() {
        Instant addedAt = Instant.parse("2025-03-02T10:00:00Z");
        when(service.addSigner(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(Single.just(new Member(CUSTOMER_ID, addedAt)));

        StepVerifier.create(controller.addAccountSigner(ACCOUNT_ID,
                        Mono.just(new AddAccountMemberRequest().customerId(CUSTOMER_ID)), null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getHeaders().getLocation()).isNull();
                    assertThat(response.getBody().getCustomerId()).isEqualTo(CUSTOMER_ID);
                })
                .verifyComplete();
    }

    @Test
    void addSignerPropagatesBadRequest() {
        when(service.addSigner(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Single.error(new BusinessException(HttpStatus.BAD_REQUEST, "personal account")));

        StepVerifier.create(controller.addAccountSigner(ACCOUNT_ID,
                        Mono.just(new AddAccountMemberRequest().customerId(CUSTOMER_ID)), null))
                .expectErrorMatches(error -> error instanceof BusinessException
                        && ((BusinessException) error).getStatus() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void listSignersReturnsOkWithMembers() {
        Member member = new Member(CUSTOMER_ID, Instant.parse("2025-03-02T10:00:00Z"));
        when(service.listSigners(ACCOUNT_ID)).thenReturn(Single.just(List.of(member)));

        StepVerifier.create(controller.listAccountSigners(ACCOUNT_ID, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().collectList().block())
                            .extracting(dto -> dto.getCustomerId())
                            .containsExactly(CUSTOMER_ID);
                })
                .verifyComplete();
    }

    @Test
    void removeSignerReturnsNoContent() {
        when(service.removeSigner(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(Completable.complete());

        StepVerifier.create(controller.removeAccountSigner(ACCOUNT_ID, CUSTOMER_ID, null))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                .verifyComplete();
        verify(service).removeSigner(ACCOUNT_ID, CUSTOMER_ID);
    }

    @Test
    void removeSignerPropagatesNotFound() {
        when(service.removeSigner(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Completable.error(new BusinessException(HttpStatus.NOT_FOUND, "missing")));

        StepVerifier.create(controller.removeAccountSigner(ACCOUNT_ID, CUSTOMER_ID, null))
                .expectError(BusinessException.class)
                .verify();
    }
}
