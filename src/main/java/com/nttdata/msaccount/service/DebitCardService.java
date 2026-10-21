package com.nttdata.msaccount.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.nttdata.msaccount.exception.BusinessException;
import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.DebitCardStatus;
import com.nttdata.msaccount.model.IssuedDebitCard;
import com.nttdata.msaccount.repository.AccountRepository;
import com.nttdata.msaccount.repository.IssuedDebitCardRepository;
import com.nttdata.msaccount.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DebitCardService {

    private final AccountRepository accounts;
    private final IssuedDebitCardRepository cards;

    public Single<IssuedDebitCard> issue(String accountId) {
        return loadAccount(accountId).flatMap(account -> {
            if (account.getStatus() != AccountStatus.ACTIVE) {
                return Single.error(new BusinessException(HttpStatus.CONFLICT,
                        "debit cards can only be issued for active accounts"));
            }
            IssuedDebitCard card = IssuedDebitCard.builder()
                    .id(UUID.randomUUID().toString())
                    .accountId(accountId)
                    .customerId(account.getCustomerId())
                    .status(DebitCardStatus.ACTIVE)
                    .createdAt(Instant.now())
                    .build();
            return ReactiveBridge.toSingle(cards.save(card));
        });
    }

    public Single<List<IssuedDebitCard>> list(String accountId) {
        return loadAccount(accountId).flatMap(account ->
                ReactiveBridge.toFlowable(cards.findByAccountId(accountId)).toList());
    }

    public Maybe<IssuedDebitCard> findActive(String debitCardId) {
        return ReactiveBridge.toMaybe(cards.findById(debitCardId))
                .filter(card -> card.getStatus() == DebitCardStatus.ACTIVE);
    }

    private Single<Account> loadAccount(String accountId) {
        return ReactiveBridge.toMaybe(accounts.findById(accountId))
                .switchIfEmpty(Single.error(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "account " + accountId + " not found")));
    }
}
