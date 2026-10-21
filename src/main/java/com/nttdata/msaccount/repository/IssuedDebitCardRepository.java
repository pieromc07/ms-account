package com.nttdata.msaccount.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.nttdata.msaccount.model.IssuedDebitCard;

import reactor.core.publisher.Flux;

@Repository
public interface IssuedDebitCardRepository extends ReactiveMongoRepository<IssuedDebitCard, String> {

    Flux<IssuedDebitCard> findByAccountId(String accountId);
}
