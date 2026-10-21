package com.nttdata.msaccount.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.nttdata.msaccount.model.CreditCardProjection;

import reactor.core.publisher.Mono;

@Repository
public interface CreditCardProjectionRepository extends ReactiveMongoRepository<CreditCardProjection, String> {

    Mono<Boolean> existsByCustomerIdAndActiveTrue(String customerId);
}
