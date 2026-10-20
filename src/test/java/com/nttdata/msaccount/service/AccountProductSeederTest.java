package com.nttdata.msaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nttdata.msaccount.config.AccountProperties;
import com.nttdata.msaccount.model.AccountProduct;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.repository.AccountProductRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccountProductSeederTest {

    @Mock
    private AccountProductRepository repository;

    private AccountProductSeeder seeder;

    @Test
    void seedsDefaultProductsWhenCatalogIsEmpty() {
        seeder = new AccountProductSeeder(repository, properties(BigDecimal.ZERO));
        when(repository.count()).thenReturn(Mono.just(0L));
        when(repository.saveAll(anyIterable())).thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));

        seeder.seedIfEmpty();

        verify(repository).saveAll(anyIterable());
    }

    @Test
    void doesNotSeedWhenCatalogAlreadyHasProducts() {
        seeder = new AccountProductSeeder(repository, properties(BigDecimal.ZERO));
        when(repository.count()).thenReturn(Mono.just(3L));

        seeder.seedIfEmpty();

        verify(repository, never()).saveAll(anyIterable());
    }

    @Test
    void defaultCatalogMatchesBusinessRules() {
        seeder = new AccountProductSeeder(repository, properties(new BigDecimal("50.00")));

        List<AccountProduct> products = seeder.defaultProducts();

        assertThat(products).hasSize(6);
        assertThat(products).allSatisfy(product -> assertThat(product.getMinimumOpeningAmount())
                .isEqualByComparingTo("50.00"));
        assertThat(products).filteredOn(AccountProduct::isRequiresCreditCard)
                .extracting(AccountProduct::getCode)
                .containsExactlyInAnyOrder("SAVINGS_VIP", "CURRENT_PYME");
        assertThat(products).filteredOn(product -> product.getAccountType() == AccountType.SAVINGS)
                .extracting(AccountProduct::getCode)
                .containsExactlyInAnyOrder("SAVINGS_STANDARD", "SAVINGS_VIP");
    }

    private static AccountProperties properties(BigDecimal minimumOpeningAmount) {
        return new AccountProperties(new AccountProperties.Rules(minimumOpeningAmount), null);
    }
}
