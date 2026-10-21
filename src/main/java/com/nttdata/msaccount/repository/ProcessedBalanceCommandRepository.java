package com.nttdata.msaccount.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.nttdata.msaccount.model.ProcessedBalanceCommand;

@Repository
public interface ProcessedBalanceCommandRepository extends ReactiveMongoRepository<ProcessedBalanceCommand, String> {
}
