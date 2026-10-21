package com.nttdata.msaccount.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command asking ms-account to credit or debit an account balance. The transaction id is the idempotency key.
 */
public record BalanceCommand(
        int schemaVersion,
        String transactionId,
        String accountId,
        Operation operation,
        BigDecimal amount,
        Instant occurredAt) {

    public static final String TOPIC = "account.balance.commands";
    public static final int SCHEMA_VERSION = 1;

    public BalanceCommand(String transactionId, String accountId, Operation operation,
            BigDecimal amount, Instant occurredAt) {
        this(SCHEMA_VERSION, transactionId, accountId, operation, amount, occurredAt);
    }

    public enum Operation {
        DEPOSIT,
        WITHDRAWAL
    }
}
