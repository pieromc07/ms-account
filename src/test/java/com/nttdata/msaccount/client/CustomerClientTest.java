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
import com.nttdata.msaccount.model.CustomerProfile;
import com.nttdata.msaccount.model.CustomerType;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CustomerClientTest {

    private static final String CUSTOMER_ID = "64b000000000000000000002";

    private final AtomicReference<String> requestedUrl = new AtomicReference<>();

    private CustomerClient clientReturning(Mono<ClientResponse> response, Duration timeout) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://customer:8080")
                .exchangeFunction(request -> {
                    requestedUrl.set(request.url().toString());
                    return response;
                })
                .build();
        return new CustomerClient(webClient, timeout);
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    @Test
    void findByIdMapsCustomerAndIgnoresUnknownFields() {
        String body = """
                {"id":"%s","customerType":"BUSINESS","profile":"PYME","legalName":"Acme","extra":true}
                """.formatted(CUSTOMER_ID);
        CustomerClient client = clientReturning(json(HttpStatus.OK, body), Duration.ofSeconds(2));

        StepVerifier.create(client.findById(CUSTOMER_ID))
                .assertNext(info -> {
                    assertThat(info.id()).isEqualTo(CUSTOMER_ID);
                    assertThat(info.customerType()).isEqualTo(CustomerType.BUSINESS);
                    assertThat(info.profile()).isEqualTo(CustomerProfile.PYME);
                })
                .verifyComplete();
        assertThat(requestedUrl.get()).isEqualTo("http://customer:8080/api/v1/customers/" + CUSTOMER_ID);
    }

    @Test
    void findByIdCompletesEmptyOnNotFound() {
        CustomerClient client = clientReturning(json(HttpStatus.NOT_FOUND, "{}"), Duration.ofSeconds(2));

        StepVerifier.create(client.findById(CUSTOMER_ID)).verifyComplete();
    }

    @Test
    void findByIdFailsWithDependencyErrorOnServerError() {
        CustomerClient client = clientReturning(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}"), Duration.ofSeconds(2));

        StepVerifier.create(client.findById(CUSTOMER_ID))
                .expectError(DependencyUnavailableException.class)
                .verify();
    }

    @Test
    void findByIdFailsWithDependencyErrorOnConnectionFailure() {
        CustomerClient client = clientReturning(Mono.error(new IOException("connection refused")),
                Duration.ofSeconds(2));

        StepVerifier.create(client.findById(CUSTOMER_ID))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DependencyUnavailableException.class)
                        .hasCauseInstanceOf(IOException.class))
                .verify();
    }

    @Test
    void findByIdFailsWithDependencyErrorOnTimeout() {
        CustomerClient client = clientReturning(Mono.never(), Duration.ofMillis(50));

        StepVerifier.create(client.findById(CUSTOMER_ID))
                .expectError(DependencyUnavailableException.class)
                .verify();
    }
}
