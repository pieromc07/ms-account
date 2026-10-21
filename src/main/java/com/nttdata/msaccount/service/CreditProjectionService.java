package com.nttdata.msaccount.service;

import org.springframework.stereotype.Service;

import com.nttdata.msaccount.event.CreditCardEvent;
import com.nttdata.msaccount.event.OverdueDebtEvent;
import com.nttdata.msaccount.model.CreditCardProjection;
import com.nttdata.msaccount.model.OverdueDebtProjection;
import com.nttdata.msaccount.repository.CreditCardProjectionRepository;
import com.nttdata.msaccount.repository.OverdueDebtProjectionRepository;
import com.nttdata.msaccount.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

/**
 * Keeps local projections of credit state so account rules do not need synchronous calls to ms-credit.
 * Events older than the stored state are ignored, so redelivered or out-of-order events cannot roll it back.
 */
@Service
@RequiredArgsConstructor
public class CreditProjectionService {

    private final CreditCardProjectionRepository cardProjections;
    private final OverdueDebtProjectionRepository overdueProjections;

    public Single<Boolean> hasActiveCard(String customerId) {
        return ReactiveBridge.toSingle(cardProjections.existsByCustomerIdAndActiveTrue(customerId));
    }

    public Single<Boolean> isOverdue(String customerId) {
        return ReactiveBridge.toMaybe(overdueProjections.findById(customerId))
                .map(OverdueDebtProjection::isOverdue)
                .defaultIfEmpty(false);
    }

    public Completable applyCardEvent(CreditCardEvent event) {
        boolean active = event.eventType() == CreditCardEvent.EventType.CREATED;
        return ReactiveBridge.toMaybe(cardProjections.findById(event.creditCardId()))
                .map(existing -> existing.getOccurredAt().isAfter(event.occurredAt()))
                .defaultIfEmpty(false)
                .flatMapCompletable(stale -> stale
                        ? Completable.complete()
                        : ReactiveBridge.toSingle(cardProjections.save(new CreditCardProjection(
                                event.creditCardId(), event.customerId(), active, event.occurredAt())))
                                .ignoreElement());
    }

    public Completable applyOverdueEvent(OverdueDebtEvent event) {
        boolean overdue = event.eventType() == OverdueDebtEvent.EventType.OVERDUE_DEBT_DETECTED;
        return ReactiveBridge.toMaybe(overdueProjections.findById(event.customerId()))
                .map(existing -> existing.getOccurredAt().isAfter(event.occurredAt()))
                .defaultIfEmpty(false)
                .flatMapCompletable(stale -> stale
                        ? Completable.complete()
                        : ReactiveBridge.toSingle(overdueProjections.save(new OverdueDebtProjection(
                                event.customerId(), overdue, event.occurredAt())))
                                .ignoreElement());
    }
}
