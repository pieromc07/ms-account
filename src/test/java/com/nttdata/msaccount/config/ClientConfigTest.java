package com.nttdata.msaccount.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

import com.nttdata.msaccount.client.CreditCardClient;
import com.nttdata.msaccount.client.CustomerClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

class ClientConfigTest {

    private static final AccountProperties PROPERTIES = new AccountProperties(
            new AccountProperties.Rules(BigDecimal.ZERO),
            new AccountProperties.Clients("http://customer:8080", "http://credit:8082", Duration.ofSeconds(2)));

    @Test
    void createsClientsWithConfiguredBaseUrls() {
        ClientConfig config = new ClientConfig();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        CustomerClient customerClient = config.customerClient(WebClient.builder(), PROPERTIES, registry);
        CreditCardClient creditCardClient = config.creditCardClient(WebClient.builder(), PROPERTIES, registry);

        assertThat(customerClient).isNotNull();
        assertThat(creditCardClient).isNotNull();
    }

    @Test
    void wiresBuilderAndBothClientsIntoContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClientConfig.class)
                .withBean(AccountProperties.class, () -> PROPERTIES)
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WebClient.Builder.class);
                    assertThat(context).hasSingleBean(CustomerClient.class);
                    assertThat(context).hasSingleBean(CreditCardClient.class);
                });
    }
}
