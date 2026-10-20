package com.nttdata.msaccount.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.nttdata.msaccount.exception.DependencyUnavailableException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CreditCardClientTest {

    private static final String CUSTOMER_ID = "64b000000000000000000002";

    private final AtomicReference<String> requestedUrl = new AtomicReference<>();

    private CreditCardClient clientReturning(Mono<ClientResponse> response, Duration timeout) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://credit:8082")
                .exchangeFunction(request -> {
                    requestedUrl.set(request.url().toString());
                    return response;
                })
                .build();
        return new CreditCardClient(webClient, timeout);
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    @Test
    void hasCreditCardIsTrueWhenCardsAreReturned() {
        CreditCardClient client = clientReturning(
                json(HttpStatus.OK, "[{\"id\":\"card-1\",\"extra\":\"x\"}]"), Duration.ofSeconds(2));

        StepVerifier.create(client.hasCreditCard(CUSTOMER_ID))
                .expectNext(true)
                .verifyComplete();
        assertThat(requestedUrl.get())
                .isEqualTo("http://credit:8082/api/v1/credit-cards?customerId=" + CUSTOMER_ID);
    }

    @Test
    void hasCreditCardIsFalseWhenNoCardsAreReturned() {
        CreditCardClient client = clientReturning(json(HttpStatus.OK, "[]"), Duration.ofSeconds(2));

        StepVerifier.create(client.hasCreditCard(CUSTOMER_ID))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void hasCreditCardFailsWithDependencyErrorOnServerError() {
        CreditCardClient client = clientReturning(json(HttpStatus.BAD_GATEWAY, "{}"), Duration.ofSeconds(2));

        StepVerifier.create(client.hasCreditCard(CUSTOMER_ID))
                .expectError(DependencyUnavailableException.class)
                .verify();
    }

    @Test
    void hasCreditCardFailsWithDependencyErrorOnConnectionFailure() {
        CreditCardClient client = clientReturning(Mono.error(new IOException("connection refused")),
                Duration.ofSeconds(2));

        StepVerifier.create(client.hasCreditCard(CUSTOMER_ID))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DependencyUnavailableException.class)
                        .hasCauseInstanceOf(IOException.class))
                .verify();
    }

    @Test
    void hasCreditCardFailsWithDependencyErrorOnTimeout() {
        CreditCardClient client = clientReturning(Mono.never(), Duration.ofMillis(50));

        StepVerifier.create(client.hasCreditCard(CUSTOMER_ID))
                .expectError(DependencyUnavailableException.class)
                .verify();
    }
}
