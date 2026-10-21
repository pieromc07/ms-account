package com.nttdata.msaccount.event;

import java.time.Instant;

/**
 * Reply from ms-account for a {@link BalanceCommand}. The transaction id links the reply to its movement.
 */
public record BalanceResult(
        int schemaVersion,
        String transactionId,
        String accountId,
        Outcome outcome,
        String reason,
        Instant occurredAt) {

    public static final String TOPIC = "account.balance.results";
    public static final int SCHEMA_VERSION = 1;

    public enum Outcome {
        OK,
        REJECTED
    }
}
