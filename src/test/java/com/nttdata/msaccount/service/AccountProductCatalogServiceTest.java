package com.nttdata.msaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;

import com.nttdata.msaccount.config.CatalogProperties;
import com.nttdata.msaccount.model.AccountProduct;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerProfile;
import com.nttdata.msaccount.model.CustomerType;
import com.nttdata.msaccount.repository.AccountProductRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AccountProductCatalogServiceTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private AccountProductRepository repository;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AccountProductCatalogService service;

    @BeforeEach
    void setUp() {
        service = new AccountProductCatalogService(repository, redisTemplate, jsonMapper,
                new CatalogProperties(TTL));
    }

    private static AccountProduct product(String code) {
        return AccountProduct.builder()
                .code(code)
                .name("Producto " + code)
                .accountType(AccountType.SAVINGS)
                .customerTypes(List.of(CustomerType.PERSONAL))
                .customerProfiles(List.of(CustomerProfile.STANDARD))
                .requiresCreditCard(false)
                .minimumOpeningAmount(BigDecimal.ZERO)
                .build();
    }

    @Test
    void servesFromCacheWithoutQueryingMongo() throws Exception {
        String json = jsonMapper.writeValueAsString(List.of(product("B"), product("A")));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(AccountProductCatalogService.CACHE_KEY)).thenReturn(Mono.just(json));

        List<AccountProduct> result = service.listProducts().blockingGet();

        assertThat(result).extracting(AccountProduct::getCode).containsExactly("B", "A");
        verify(repository, never()).findAll();
    }

    @Test
    void loadsFromMongoSortsAndCachesOnMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(AccountProductCatalogService.CACHE_KEY)).thenReturn(Mono.empty());
        when(repository.findAll()).thenReturn(Flux.just(product("CURRENT_PYME"), product("SAVINGS_VIP")));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        List<AccountProduct> result = service.listProducts().blockingGet();

        assertThat(result).extracting(AccountProduct::getCode).containsExactly("CURRENT_PYME", "SAVINGS_VIP");
        verify(valueOperations).set(AccountProductCatalogService.CACHE_KEY,
                jsonMapper.writeValueAsString(result), TTL);
    }

    @Test
    void fallsBackToMongoWhenRedisReadFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(AccountProductCatalogService.CACHE_KEY))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));
        when(repository.findAll()).thenReturn(Flux.just(product("SAVINGS_STANDARD")));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        List<AccountProduct> result = service.listProducts().blockingGet();

        assertThat(result).extracting(AccountProduct::getCode).containsExactly("SAVINGS_STANDARD");
    }

    @Test
    void fallsBackToMongoWhenCachedJsonIsCorrupt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(AccountProductCatalogService.CACHE_KEY)).thenReturn(Mono.just("{not-json"));
        when(repository.findAll()).thenReturn(Flux.just(product("FIXED_TERM_PERSONAL")));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        List<AccountProduct> result = service.listProducts().blockingGet();

        assertThat(result).extracting(AccountProduct::getCode).containsExactly("FIXED_TERM_PERSONAL");
    }

    @Test
    void returnsMongoDataEvenWhenCacheWriteFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(AccountProductCatalogService.CACHE_KEY)).thenReturn(Mono.empty());
        when(repository.findAll()).thenReturn(Flux.just(product("CURRENT_BUSINESS")));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new IllegalStateException("redis write failed")));

        List<AccountProduct> result = service.listProducts().blockingGet();

        assertThat(result).extracting(AccountProduct::getCode).containsExactly("CURRENT_BUSINESS");
    }
}
