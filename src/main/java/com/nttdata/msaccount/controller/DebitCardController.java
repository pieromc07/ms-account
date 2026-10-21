package com.nttdata.msaccount.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.nttdata.msaccount.generated.api.DebitCardsApi;
import com.nttdata.msaccount.generated.dto.DebitCard;
import com.nttdata.msaccount.mapper.AccountMapper;
import com.nttdata.msaccount.service.DebitCardService;
import com.nttdata.msaccount.util.ReactiveBridge;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class DebitCardController implements DebitCardsApi {

    private final DebitCardService service;
    private final AccountMapper mapper;

    @Override
    public Mono<ResponseEntity<DebitCard>> createDebitCard(String accountId, ServerWebExchange exchange) {
        return ReactiveBridge.toMono(service.issue(accountId))
                .map(card -> ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDebitCardDto(card)));
    }

    @Override
    public Mono<ResponseEntity<Flux<DebitCard>>> listDebitCards(String accountId, ServerWebExchange exchange) {
        return ReactiveBridge.toMono(service.list(accountId))
                .map(cards -> ResponseEntity.ok(Flux.fromIterable(cards).map(mapper::toDebitCardDto)));
    }
}
