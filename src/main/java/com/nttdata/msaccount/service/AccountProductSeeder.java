package com.nttdata.msaccount.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nttdata.msaccount.config.AccountProperties;
import com.nttdata.msaccount.model.AccountProduct;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerProfile;
import com.nttdata.msaccount.model.CustomerType;
import com.nttdata.msaccount.repository.AccountProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the default product catalog on first startup. Mirrors the rules enforced by {@link AccountService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountProductSeeder {

    private final AccountProductRepository repository;
    private final AccountProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        repository.count()
                .filter(count -> count == 0)
                .flatMapMany(count -> repository.saveAll(defaultProducts()))
                .then()
                .subscribe(null, error -> log.error("Could not seed the account product catalog", error));
    }

    List<AccountProduct> defaultProducts() {
        BigDecimal minimum = properties.rules().minimumOpeningAmount();
        return List.of(
                AccountProduct.builder()
                        .code("SAVINGS_STANDARD")
                        .name("Cuenta de ahorro estándar")
                        .accountType(AccountType.SAVINGS)
                        .customerTypes(List.of(CustomerType.PERSONAL))
                        .customerProfiles(List.of(CustomerProfile.STANDARD))
                        .requiresCreditCard(false)
                        .minimumOpeningAmount(minimum)
                        .build(),
                AccountProduct.builder()
                        .code("SAVINGS_VIP")
                        .name("Cuenta de ahorro VIP")
                        .accountType(AccountType.SAVINGS)
                        .customerTypes(List.of(CustomerType.PERSONAL))
                        .customerProfiles(List.of(CustomerProfile.VIP))
                        .requiresCreditCard(true)
                        .minimumOpeningAmount(minimum)
                        .build(),
                AccountProduct.builder()
                        .code("CURRENT_PERSONAL")
                        .name("Cuenta corriente personal")
                        .accountType(AccountType.CURRENT)
                        .customerTypes(List.of(CustomerType.PERSONAL))
                        .customerProfiles(List.of(CustomerProfile.STANDARD, CustomerProfile.VIP))
                        .requiresCreditCard(false)
                        .minimumOpeningAmount(minimum)
                        .build(),
                AccountProduct.builder()
                        .code("FIXED_TERM_PERSONAL")
                        .name("Cuenta a plazo fijo personal")
                        .accountType(AccountType.FIXED_TERM)
                        .customerTypes(List.of(CustomerType.PERSONAL))
                        .customerProfiles(List.of(CustomerProfile.STANDARD, CustomerProfile.VIP))
                        .requiresCreditCard(false)
                        .minimumOpeningAmount(minimum)
                        .build(),
                AccountProduct.builder()
                        .code("CURRENT_BUSINESS")
                        .name("Cuenta corriente empresarial")
                        .accountType(AccountType.CURRENT)
                        .customerTypes(List.of(CustomerType.BUSINESS))
                        .customerProfiles(List.of(CustomerProfile.STANDARD))
                        .requiresCreditCard(false)
                        .minimumOpeningAmount(minimum)
                        .build(),
                AccountProduct.builder()
                        .code("CURRENT_PYME")
                        .name("Cuenta corriente PYME")
                        .accountType(AccountType.CURRENT)
                        .customerTypes(List.of(CustomerType.BUSINESS))
                        .customerProfiles(List.of(CustomerProfile.PYME))
                        .requiresCreditCard(true)
                        .minimumOpeningAmount(minimum)
                        .build());
    }
}
