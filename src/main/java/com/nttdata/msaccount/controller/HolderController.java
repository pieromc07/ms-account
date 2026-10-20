package com.nttdata.msaccount.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.nttdata.msaccount.generated.api.HoldersApi;
import com.nttdata.msaccount.generated.dto.AccountMember;
import com.nttdata.msaccount.generated.dto.AddAccountMemberRequest;
import com.nttdata.msaccount.mapper.AccountMapper;
import com.nttdata.msaccount.service.AccountService;
import com.nttdata.msaccount.util.ReactiveBridge;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class HolderController implements HoldersApi {

    private final AccountService service;
    private final AccountMapper mapper;

    @Override
    public Mono<ResponseEntity<AccountMember>> addAccountHolder(
            String accountId,
            Mono<AddAccountMemberRequest> addAccountMemberRequest,
            ServerWebExchange exchange) {
        return addAccountMemberRequest.flatMap(request -> ReactiveBridge.toMono(
                        service.addHolder(accountId, request.getCustomerId())))
                .map(member -> ResponseEntity.status(HttpStatus.CREATED).body(mapper.toMemberDto(member)));
    }

    @Override
    public Mono<ResponseEntity<Flux<AccountMember>>> listAccountHolders(
            String accountId,
            ServerWebExchange exchange) {
        return ReactiveBridge.toMono(service.listHolders(accountId))
                .map(members -> ResponseEntity.ok(Flux.fromIterable(members).map(mapper::toMemberDto)));
    }

    @Override
    public Mono<ResponseEntity<Void>> removeAccountHolder(
            String accountId,
            String customerId,
            ServerWebExchange exchange) {
        return ReactiveBridge.toVoid(service.removeHolder(accountId, customerId))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
