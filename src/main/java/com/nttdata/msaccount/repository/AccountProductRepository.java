package com.nttdata.msaccount.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.nttdata.msaccount.model.AccountProduct;

@Repository
public interface AccountProductRepository extends ReactiveMongoRepository<AccountProduct, String> {
}
