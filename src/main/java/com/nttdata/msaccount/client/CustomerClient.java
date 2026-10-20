package com.nttdata.msaccount.client;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nttdata.msaccount.exception.DependencyUnavailableException;
import com.nttdata.msaccount.model.CustomerProfile;
import com.nttdata.msaccount.model.CustomerType;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

public class CustomerClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final CircuitBreaker circuitBreaker;

    public CustomerClient(WebClient webClient, Duration timeout, CircuitBreaker circuitBreaker) {
        this.webClient = webClient;
        this.timeout = timeout;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Emits the customer, or completes empty when ms-customer answers 404.
     */
    public Mono<CustomerInfo> findById(String customerId) {
        return webClient.get()
                .uri("/api/v1/customers/{id}", customerId)
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
                        return response.releaseBody().then(Mono.<CustomerInfo>empty());
                    }
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(CustomerInfo.class);
                    }
                    return response.releaseBody().then(Mono.<CustomerInfo>error(
                            new DependencyUnavailableException("ms-customer responded with " + response.statusCode())));
                })
                .timeout(timeout)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(error -> !(error instanceof DependencyUnavailableException),
                        error -> new DependencyUnavailableException("ms-customer is unavailable", error));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerInfo(String id, CustomerType customerType, CustomerProfile profile) {
    }
}
