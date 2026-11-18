package com.nttdata.msaccount.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.nttdata.msaccount.event.BalanceCommand;
import com.nttdata.msaccount.config.AccountProperties;
import com.nttdata.msaccount.config.BusinessCalendar;
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

    static final int MAX_ATTEMPTS = 3;

    private final AccountRepository accounts;
    private final ReactiveMongoOperations mongo;
    private final AccountProperties properties;
    private final Clock clock;

    public Single<Movement> move(String accountId, BalanceCommand.Operation operation, BigDecimal amount,
            Instant occurredAt) {
        if (amount == null || amount.signum() <= 0) {
            return Single.just(Movement.rejected(accountId, "amount must be positive"));
        }
        return attempt(accountId, operation, amount, occurredAt, MAX_ATTEMPTS);
    }

    public Single<Movement> move(String accountId, BalanceCommand.Operation operation, BigDecimal amount) {
        return move(accountId, operation, amount, clock.instant());
    }

    private Single<Movement> attempt(String accountId, BalanceCommand.Operation operation, BigDecimal amount,
            Instant occurredAt, int attemptsLeft) {
        return ReactiveBridge.toMaybe(accounts.findById(accountId))
                .flatMap(account -> applyTo(account, operation, amount, occurredAt, attemptsLeft).toMaybe())
                .switchIfEmpty(Single.just(Movement.rejected(accountId, "account not found")));
    }

    private Single<Movement> applyTo(Account account, BalanceCommand.Operation operation, BigDecimal amount,
            Instant occurredAt, int attemptsLeft) {
        LocalDate operationDate = LocalDate.ofInstant(occurredAt, BusinessCalendar.ZONE);
        Optional<String> reason = rejectionReason(account, operation, amount, operationDate);
        if (reason.isPresent()) {
            return Single.just(Movement.rejected(account.getId(), reason.get()));
        }
        BigDecimal expected = account.getBalance();
        BigDecimal next = operation == BalanceCommand.Operation.DEPOSIT
                ? expected.add(amount)
                : expected.subtract(amount);
        VipAverageState averageState = vipAverageState(account, operationDate, next);
        return compareAndSet(account.getId(), expected, next, averageState).flatMap(applied -> {
            if (applied) {
                return Single.just(Movement.ok(account.getId()));
            }
            if (attemptsLeft > 1) {
                return attempt(account.getId(), operation, amount, occurredAt, attemptsLeft - 1);
            }
            return Single.error(new IllegalStateException(
                    "balance of account " + account.getId() + " changed concurrently; retry later"));
        });
    }

    private Optional<String> rejectionReason(Account account, BalanceCommand.Operation operation,
            BigDecimal amount, LocalDate operationDate) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return Optional.of("account is not active");
        }
        if (account.getAccountType() == AccountType.FIXED_TERM
                && operationDate.getDayOfMonth() != properties.rules().fixedTermAllowedDayOfMonth()) {
            return Optional.of("fixed-term accounts only allow deposits and withdrawals on day "
                    + properties.rules().fixedTermAllowedDayOfMonth());
        }
        if (operation == BalanceCommand.Operation.DEPOSIT) {
            return Optional.empty();
        }
        BigDecimal after = account.getBalance().subtract(amount);
        if (after.signum() < 0) {
            return Optional.of("insufficient balance");
        }
        if (isVipSavings(account)
                && projectedAverage(account, operationDate, after).compareTo(
                        properties.rules().vipMinimumMonthlyAverage()) < 0) {
            return Optional.of("withdrawal would reduce the projected monthly average below S/ "
                    + properties.rules().vipMinimumMonthlyAverage());
        }
        return Optional.empty();
    }

    private VipAverageState vipAverageState(Account account, LocalDate operationDate, BigDecimal nextBalance) {
        if (!isVipSavings(account)) {
            return null;
        }
        YearMonth currentMonth = YearMonth.from(operationDate);
        LocalDate startDate = account.getCreatedAt() == null
                ? currentMonth.atDay(1)
                : max(currentMonth.atDay(1), LocalDate.ofInstant(account.getCreatedAt(), BusinessCalendar.ZONE));
        String monthKey = currentMonth.toString();
        BigDecimal closedTotal = account.getVipAverageMonth() == null
                || !account.getVipAverageMonth().equals(monthKey)
                        ? BigDecimal.ZERO
                        : Optional.ofNullable(account.getVipClosedBalanceTotal()).orElse(BigDecimal.ZERO);
        int closedDays = account.getVipAverageMonth() == null
                || !account.getVipAverageMonth().equals(monthKey)
                        ? 0
                        : Optional.ofNullable(account.getVipClosedDays()).orElse(0);
        LocalDate lastDate = account.getVipAverageMonth() == null
                || !account.getVipAverageMonth().equals(monthKey)
                        || account.getVipLastBalanceDate() == null
                        ? startDate
                        : account.getVipLastBalanceDate();
        long daysToClose = Math.max(0, ChronoUnit.DAYS.between(lastDate, operationDate));
        closedTotal = closedTotal.add(account.getBalance().multiply(BigDecimal.valueOf(daysToClose)));
        closedDays += (int) daysToClose;
        return new VipAverageState(monthKey, closedTotal, closedDays, operationDate);
    }

    private BigDecimal projectedAverage(Account account, LocalDate operationDate, BigDecimal after) {
        VipAverageState state = vipAverageState(account, operationDate, after);
        LocalDate startDate = account.getCreatedAt() == null
                ? YearMonth.from(operationDate).atDay(1)
                : max(YearMonth.from(operationDate).atDay(1),
                        LocalDate.ofInstant(account.getCreatedAt(), BusinessCalendar.ZONE));
        int activeDays = (int) ChronoUnit.DAYS.between(startDate, YearMonth.from(operationDate).atEndOfMonth()) + 1;
        int remainingDays = activeDays - state.closedDays();
        BigDecimal projectedTotal = state.closedTotal()
                .add(after.multiply(BigDecimal.valueOf(remainingDays)));
        return projectedTotal.divide(BigDecimal.valueOf(activeDays), 2, java.math.RoundingMode.HALF_UP);
    }

    private static LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static boolean isVipSavings(Account account) {
        return account.getCustomerProfile() == CustomerProfile.VIP
                && account.getAccountType() == AccountType.SAVINGS;
    }

    private Single<Boolean> compareAndSet(String accountId, BigDecimal expected, BigDecimal next,
            VipAverageState averageState) {
        Query query = Query.query(Criteria.where("_id").is(accountId)
                .and("status").is(AccountStatus.ACTIVE)
                .and("balance").is(expected));
        Update update = new Update().set("balance", next).set("updatedAt", clock.instant());
        if (averageState != null) {
            update.set("vipAverageMonth", averageState.month())
                    .set("vipClosedBalanceTotal", averageState.closedTotal())
                    .set("vipClosedDays", averageState.closedDays())
                    .set("vipLastBalanceDate", averageState.lastDate());
        }
        return ReactiveBridge.toSingle(mongo.updateFirst(query, update, Account.class))
                .map(result -> result.getMatchedCount() == 1);
    }

    private record VipAverageState(String month, BigDecimal closedTotal, int closedDays, LocalDate lastDate) {
    }
}
