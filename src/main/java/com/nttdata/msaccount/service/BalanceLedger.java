package com.nttdata.msaccount.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.nttdata.msaccount.event.BalanceCommand;
import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerProfile;
import com.nttdata.msaccount.repository.AccountRepository;
import com.nttdata.msaccount.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

/**
 * Applies deposits and withdrawals to account balances. The balance is changed with a compare-and-set update, so a
 * concurrent write makes the attempt reload and retry instead of overwriting the other change.
 */
@Service
@RequiredArgsConstructor
public class BalanceLedger {

    static final BigDecimal VIP_SAVINGS_MINIMUM_BALANCE = new BigDecimal("2000.00");
    static final int MAX_ATTEMPTS = 3;

    private final AccountRepository accounts;
    private final ReactiveMongoOperations mongo;

    public Single<Movement> move(String accountId, BalanceCommand.Operation operation, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return Single.just(Movement.rejected(accountId, "amount must be positive"));
        }
        return attempt(accountId, operation, amount, MAX_ATTEMPTS);
    }

    private Single<Movement> attempt(String accountId, BalanceCommand.Operation operation, BigDecimal amount,
            int attemptsLeft) {
        return ReactiveBridge.toMaybe(accounts.findById(accountId))
                .flatMap(account -> applyTo(account, operation, amount, attemptsLeft).toMaybe())
                .switchIfEmpty(Single.just(Movement.rejected(accountId, "account not found")));
    }

    private Single<Movement> applyTo(Account account, BalanceCommand.Operation operation, BigDecimal amount,
            int attemptsLeft) {
        Optional<String> reason = rejectionReason(account, operation, amount);
        if (reason.isPresent()) {
            return Single.just(Movement.rejected(account.getId(), reason.get()));
        }
        BigDecimal expected = account.getBalance();
        BigDecimal next = operation == BalanceCommand.Operation.DEPOSIT
                ? expected.add(amount)
                : expected.subtract(amount);
        return compareAndSet(account.getId(), expected, next).flatMap(applied -> {
            if (applied) {
                return Single.just(Movement.ok(account.getId()));
            }
            if (attemptsLeft > 1) {
                return attempt(account.getId(), operation, amount, attemptsLeft - 1);
            }
            return Single.error(new IllegalStateException(
                    "balance of account " + account.getId() + " changed concurrently; retry later"));
        });
    }

    private Optional<String> rejectionReason(Account account, BalanceCommand.Operation operation,
            BigDecimal amount) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return Optional.of("account is not active");
        }
        if (operation == BalanceCommand.Operation.DEPOSIT) {
            return Optional.empty();
        }
        BigDecimal after = account.getBalance().subtract(amount);
        if (after.signum() < 0) {
            return Optional.of("insufficient balance");
        }
        if (isVipSavings(account) && after.compareTo(VIP_SAVINGS_MINIMUM_BALANCE) < 0) {
            return Optional.of("VIP savings account cannot fall below S/ 2,000.00");
        }
        return Optional.empty();
    }

    private static boolean isVipSavings(Account account) {
        return account.getCustomerProfile() == CustomerProfile.VIP
                && account.getAccountType() == AccountType.SAVINGS;
    }

    private Single<Boolean> compareAndSet(String accountId, BigDecimal expected, BigDecimal next) {
        Query query = Query.query(Criteria.where("_id").is(accountId)
                .and("status").is(AccountStatus.ACTIVE)
                .and("balance").is(expected));
        Update update = new Update().set("balance", next).set("updatedAt", Instant.now());
        return ReactiveBridge.toSingle(mongo.updateFirst(query, update, Account.class))
                .map(result -> result.getMatchedCount() == 1);
    }
}
