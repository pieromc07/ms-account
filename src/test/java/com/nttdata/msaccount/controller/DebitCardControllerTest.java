package com.nttdata.msaccount.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nttdata.msaccount.generated.dto.DebitCard;
import com.nttdata.msaccount.mapper.AccountMapper;
import com.nttdata.msaccount.model.DebitCardStatus;
import com.nttdata.msaccount.model.IssuedDebitCard;
import com.nttdata.msaccount.service.DebitCardService;

import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DebitCardControllerTest {

    private static final String ACCOUNT_ID = "acc-1";

    @Mock
    private DebitCardService service;

    private DebitCardController controller;

    @BeforeEach
    void setUp() {
        controller = new DebitCardController(service, new AccountMapper());
    }

    @Test
    void createDebitCardReturnsCreatedWithCardBody() {
        IssuedDebitCard issued = card("card-1");
        when(service.issue(eq(ACCOUNT_ID))).thenReturn(Single.just(issued));

        ResponseEntity<DebitCard> response = controller.createDebitCard(ACCOUNT_ID, null).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DebitCard body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo("card-1");
        assertThat(body.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(body.getCustomerId()).isEqualTo("cust-1");
        assertThat(body.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void listDebitCardsReturnsOkWithCardsStream() {
        when(service.list(eq(ACCOUNT_ID)))
                .thenReturn(Single.just(List.of(card("card-1"), card("card-2"))));

        ResponseEntity<Flux<DebitCard>> response = controller.listDebitCards(ACCOUNT_ID, null).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StepVerifier.create(response.getBody())
                .assertNext(card -> assertThat(card.getId()).isEqualTo("card-1"))
                .assertNext(card -> assertThat(card.getId()).isEqualTo("card-2"))
                .verifyComplete();
    }

    @Test
    void createDebitCardPropagatesServiceErrors() {
        when(service.issue(any())).thenReturn(Single.error(new IllegalStateException("boom")));

        StepVerifier.create(controller.createDebitCard(ACCOUNT_ID, null))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void createDebitCardMonoEmitsOnceWhenServiceSucceeds() {
        when(service.issue(eq(ACCOUNT_ID))).thenReturn(Single.just(card("card-1")));

        Mono<?> result = controller.createDebitCard(ACCOUNT_ID, null);

        StepVerifier.create(result).expectNextCount(1).verifyComplete();
    }

    private static IssuedDebitCard card(String id) {
        return IssuedDebitCard.builder()
                .id(id)
                .accountId(ACCOUNT_ID)
                .customerId("cust-1")
                .status(DebitCardStatus.ACTIVE)
                .createdAt(Instant.parse("2025-05-01T10:00:00Z"))
                .build();
    }
}
