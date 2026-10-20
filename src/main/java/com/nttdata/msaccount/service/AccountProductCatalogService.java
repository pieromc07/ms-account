package com.nttdata.msaccount.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import com.nttdata.msaccount.config.CatalogProperties;
import com.nttdata.msaccount.model.AccountProduct;
import com.nttdata.msaccount.repository.AccountProductRepository;
import com.nttdata.msaccount.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache-aside over the account product master data. Redis failures never fail the read:
 * the catalog falls back to MongoDB, which is the source of truth.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProductCatalogService {

    static final String CACHE_KEY = "account:products:catalog";
    private static final TypeReference<List<AccountProduct>> PRODUCT_LIST = new TypeReference<>() {
    };

    private final AccountProductRepository repository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final CatalogProperties properties;

    public Single<List<AccountProduct>> listProducts() {
        return readCache().switchIfEmpty(Single.defer(this::loadAndCache));
    }

    private Maybe<List<AccountProduct>> readCache() {
        return ReactiveBridge.toMaybe(redisTemplate.opsForValue().get(CACHE_KEY))
                .map(json -> jsonMapper.readValue(json, PRODUCT_LIST))
                .doOnError(error -> log.warn("Account product cache unreadable, using MongoDB", error))
                .onErrorComplete();
    }

    private Single<List<AccountProduct>> loadAndCache() {
        return ReactiveBridge.toSingle(repository.findAll().collectList())
                .map(products -> products.stream()
                        .sorted(Comparator.comparing(AccountProduct::getCode))
                        .toList())
                .flatMap(products -> writeCache(products).andThen(Single.just(products)));
    }

    private Completable writeCache(List<AccountProduct> products) {
        return Completable.defer(() -> Completable.fromPublisher(redisTemplate.opsForValue()
                        .set(CACHE_KEY, jsonMapper.writeValueAsString(products), properties.cacheTtl())))
                .doOnError(error -> log.warn("Could not cache account products in Redis", error))
                .onErrorComplete();
    }
}
