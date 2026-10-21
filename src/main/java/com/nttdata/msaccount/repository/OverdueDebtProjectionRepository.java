package com.nttdata.msaccount.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.nttdata.msaccount.model.OverdueDebtProjection;

@Repository
public interface OverdueDebtProjectionRepository extends ReactiveMongoRepository<OverdueDebtProjection, String> {
}
