package com.nttdata.msaccount.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.nttdata.msaccount.client.CustomerClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Configuration
public class ClientConfig {

    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    CustomerClient customerClient(WebClient.Builder builder, AccountProperties properties,
            CircuitBreakerRegistry registry) {
        return new CustomerClient(
                builder.clone().baseUrl(properties.clients().customerBaseUrl()).build(),
                properties.clients().timeout(),
                registry.circuitBreaker("customer"));
    }
}
