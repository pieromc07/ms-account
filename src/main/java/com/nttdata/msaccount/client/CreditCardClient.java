package com.nttdata.msaccount.client;

import java.time.Duration;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nttdata.msaccount.exception.DependencyUnavailableException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

/**
 * Provisional: ms-credit has no documented contract yet. Assumes GET /api/v1/credit-cards?customerId=
 * returns a JSON array where a non-empty array means the customer owns a card.
 */
public class CreditCardClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final CircuitBreaker circuitBreaker;

    public CreditCardClient(WebClient webClient, Duration timeout, CircuitBreaker circuitBreaker) {
        this.webClient = webClient;
        this.timeout = timeout;
        this.circuitBreaker = circuitBreaker;
    }

    public Mono<Boolean> hasCreditCard(String customerId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/credit-cards")
                        .queryParam("customerId", customerId)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        response -> Mono.error(new DependencyUnavailableException("ms-credit responded with "
                                + response.statusCode())))
                .bodyToFlux(CreditCardSummary.class)
                .hasElements()
                .timeout(timeout)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(error -> !(error instanceof DependencyUnavailableException),
                        error -> new DependencyUnavailableException("ms-credit is unavailable", error));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreditCardSummary(String id) {
    }
}
