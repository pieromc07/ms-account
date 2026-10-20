package com.nttdata.msaccount.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.nttdata.msaccount.generated.api.AccountProductsApi;
import com.nttdata.msaccount.generated.dto.AccountProduct;
import com.nttdata.msaccount.mapper.AccountMapper;
import com.nttdata.msaccount.service.AccountProductCatalogService;
import com.nttdata.msaccount.util.ReactiveBridge;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class AccountProductController implements AccountProductsApi {

    private final AccountProductCatalogService catalogService;
    private final AccountMapper mapper;

    @Override
    public Mono<ResponseEntity<Flux<AccountProduct>>> listAccountProducts(ServerWebExchange exchange) {
        return ReactiveBridge.toMono(catalogService.listProducts())
                .map(products -> ResponseEntity.ok(
                        Flux.fromIterable(products).map(mapper::toAccountProductDto)));
    }
}
