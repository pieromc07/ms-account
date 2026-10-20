package com.nttdata.msaccount.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.nttdata.msaccount.generated.api.AccountsApi;
import com.nttdata.msaccount.generated.dto.Account;
import com.nttdata.msaccount.generated.dto.AccountStatus;
import com.nttdata.msaccount.generated.dto.AccountType;
import com.nttdata.msaccount.generated.dto.CreateAccountRequest;
import com.nttdata.msaccount.generated.dto.UpdateAccountStatusRequest;
import com.nttdata.msaccount.mapper.AccountMapper;
import com.nttdata.msaccount.service.AccountService;
import com.nttdata.msaccount.util.ReactiveBridge;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class AccountController implements AccountsApi {

    private final AccountService service;
    private final AccountMapper mapper;

    @Override
    public Mono<ResponseEntity<Account>> createAccount(
            Mono<CreateAccountRequest> createAccountRequest,
            ServerWebExchange exchange) {
        return createAccountRequest.flatMap(request -> ReactiveBridge.toMono(service.open(
                        request.getCustomerId(),
                        mapper.toModelAccountType(request.getAccountType()),
                        mapper.toBigDecimal(request.getOpeningAmount()))))
                .map(account -> ResponseEntity
                        .created(URI.create("/api/v1/accounts/" + account.getId()))
                        .body(mapper.toAccountDto(account)));
    }

    @Override
    public Mono<ResponseEntity<Flux<Account>>> listAccounts(
            String customerId,
            AccountType accountType,
            AccountStatus status,
            ServerWebExchange exchange) {
        return ReactiveBridge.toMono(service.list(
                        customerId,
                        mapper.toModelAccountType(accountType),
                        mapper.toModelAccountStatus(status)))
                .map(accounts -> ResponseEntity.ok(
                        Flux.fromIterable(accounts).map(mapper::toAccountDto)));
    }

    @Override
    public Mono<ResponseEntity<Account>> getAccountById(String accountId, ServerWebExchange exchange) {
        return ReactiveBridge.toMono(service.findById(accountId))
                .map(account -> ResponseEntity.ok(mapper.toAccountDto(account)));
    }

    @Override
    public Mono<ResponseEntity<Account>> updateAccountStatus(
            String accountId,
            Mono<UpdateAccountStatusRequest> updateAccountStatusRequest,
            ServerWebExchange exchange) {
        return updateAccountStatusRequest.flatMap(request -> ReactiveBridge.toMono(service.updateStatus(
                        accountId,
                        mapper.toModelAccountStatus(request.getStatus()))))
                .map(account -> ResponseEntity.ok(mapper.toAccountDto(account)));
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteAccount(String accountId, ServerWebExchange exchange) {
        return ReactiveBridge.toVoid(service.deactivate(accountId))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
