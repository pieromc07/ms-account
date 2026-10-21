package com.nttdata.msaccount.event;

import java.time.Instant;

/**
 * Overdue-debt transition consumed by ms-account. Proposed layout, not part of the approved contracts.
 */
public record OverdueDebtEvent(
        int schemaVersion,
        String customerId,
        EventType eventType,
        Instant occurredAt) {

    public static final String TOPIC = "credit.overdue.events";
    public static final int SCHEMA_VERSION = 1;

    public enum EventType {
        OVERDUE_DEBT_DETECTED,
        OVERDUE_DEBT_CLEARED
    }
}
