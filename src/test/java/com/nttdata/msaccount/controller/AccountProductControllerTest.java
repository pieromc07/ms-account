package com.nttdata.msaccount.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.nttdata.msaccount.generated.dto.AccountProduct;
import com.nttdata.msaccount.generated.dto.AccountType;
import com.nttdata.msaccount.generated.dto.CustomerProfile;
import com.nttdata.msaccount.generated.dto.CustomerType;
import com.nttdata.msaccount.mapper.AccountMapper;
import com.nttdata.msaccount.service.AccountProductCatalogService;

import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AccountProductControllerTest {

    @Mock
    private AccountProductCatalogService catalogService;

    private AccountProductController controller;

    @Test
    void returnsCatalogMappedToApiDtos() {
        controller = new AccountProductController(catalogService, new AccountMapper());
        when(catalogService.listProducts()).thenReturn(Single.just(List.of(
                com.nttdata.msaccount.model.AccountProduct.builder()
                        .code("CURRENT_PYME")
                        .name("Cuenta corriente PYME")
                        .accountType(com.nttdata.msaccount.model.AccountType.CURRENT)
                        .customerTypes(List.of(com.nttdata.msaccount.model.CustomerType.BUSINESS))
                        .customerProfiles(List.of(com.nttdata.msaccount.model.CustomerProfile.PYME))
                        .requiresCreditCard(true)
                        .minimumOpeningAmount(new BigDecimal("0.00"))
                        .build())));

        ResponseEntity<Flux<AccountProduct>> response = controller.listAccountProducts(null).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<AccountProduct> body = response.getBody().collectList().block();
        assertThat(body).hasSize(1);
        AccountProduct dto = body.get(0);
        assertThat(dto.getCode()).isEqualTo("CURRENT_PYME");
        assertThat(dto.getAccountType()).isEqualTo(AccountType.CURRENT);
        assertThat(dto.getCustomerTypes()).containsExactly(CustomerType.BUSINESS);
        assertThat(dto.getCustomerProfiles()).containsExactly(CustomerProfile.PYME);
        assertThat(dto.getRequiresCreditCard()).isTrue();
        assertThat(dto.getMinimumOpeningAmount()).isEqualTo(0.0);
    }
}
