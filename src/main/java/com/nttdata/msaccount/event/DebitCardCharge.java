package com.nttdata.msaccount.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Debit-card charge sent by ms-transaction. Proposed layout, not part of the approved contracts.
 */
public record DebitCardCharge(
        int schemaVersion,
        String transactionId,
        String debitCardId,
        BigDecimal amount,
        Instant occurredAt) {

    public static final String TOPIC = "account.debit.commands";
    public static final String RESULT_TOPIC = "account.debit.results";
    public static final int SCHEMA_VERSION = 1;
}
