package com.nttdata.msaccount.service;

import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.nttdata.msaccount.event.AccountEventPublisher;
import com.nttdata.msaccount.event.BalanceCommand;
import com.nttdata.msaccount.event.BalanceResult;
import com.nttdata.msaccount.event.DebitCardCharge;
import com.nttdata.msaccount.model.ProcessedBalanceCommand;
import com.nttdata.msaccount.repository.ProcessedBalanceCommandRepository;
import com.nttdata.msaccount.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

/**
 * Handles balance commands from ms-transaction and debit-card charges. Each transaction id is applied at most once:
 * a redelivered command republishes the stored outcome instead of moving the balance again.
 */
@Service
@RequiredArgsConstructor
public class AccountCommandHandler {

    private final BalanceLedger ledger;
    private final DebitCardService debitCards;
    private final ProcessedBalanceCommandRepository processed;
    private final AccountEventPublisher publisher;

    public Completable handleBalance(BalanceCommand command) {
        return replyOnce(command.transactionId(),
                () -> ledger.move(command.accountId(), command.operation(), command.amount(), command.occurredAt()),
                BalanceResult.TOPIC);
    }

    public Completable handleDebitCharge(DebitCardCharge charge) {
        return replyOnce(charge.transactionId(),
                () -> debitCards.findActive(charge.debitCardId())
                .flatMap(card -> ledger.move(
                        card.getAccountId(), BalanceCommand.Operation.WITHDRAWAL, charge.amount(),
                        charge.occurredAt()).toMaybe())
                        .switchIfEmpty(Single.just(Movement.rejected(null, "debit card not found or inactive"))),
                DebitCardCharge.RESULT_TOPIC);
    }

    private Completable replyOnce(String transactionId, Supplier<Single<Movement>> movement, String resultTopic) {
        return ReactiveBridge.toMaybe(processed.findById(transactionId))
                .switchIfEmpty(Single.defer(() -> movement.get().flatMap(result -> record(transactionId, result))))
                .flatMapCompletable(stored -> publishResult(resultTopic, transactionId, stored));
    }

    private Single<ProcessedBalanceCommand> record(String transactionId, Movement movement) {
        ProcessedBalanceCommand stored = ProcessedBalanceCommand.builder()
                .transactionId(transactionId)
                .accountId(movement.accountId())
                .outcome(movement.outcome())
                .reason(movement.reason())
                .processedAt(Instant.now())
                .build();
        return ReactiveBridge.toSingle(processed.save(stored));
    }

    private Completable publishResult(String topic, String transactionId, ProcessedBalanceCommand stored) {
        BalanceResult result = new BalanceResult(BalanceResult.SCHEMA_VERSION, transactionId,
                stored.getAccountId(), stored.getOutcome(), stored.getReason(), stored.getProcessedAt());
        return publisher.publish(topic, stored.getAccountId(), result);
    }
}
