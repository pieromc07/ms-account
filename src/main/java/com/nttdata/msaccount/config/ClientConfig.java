package com.nttdata.msaccount.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.nttdata.msaccount.client.CreditCardClient;
import com.nttdata.msaccount.client.CustomerClient;

@Configuration
public class ClientConfig {

    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    CustomerClient customerClient(WebClient.Builder builder, AccountProperties properties) {
        return new CustomerClient(
                builder.clone().baseUrl(properties.clients().customerBaseUrl()).build(),
                properties.clients().timeout());
    }

    @Bean
    CreditCardClient creditCardClient(WebClient.Builder builder, AccountProperties properties) {
        return new CreditCardClient(
                builder.clone().baseUrl(properties.clients().creditBaseUrl()).build(),
                properties.clients().timeout());
    }
}
