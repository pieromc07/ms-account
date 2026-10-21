package com.nttdata.msaccount.event;

import java.time.Instant;

import com.nttdata.msaccount.model.AccountType;

public record AccountOpened(
        int schemaVersion,
        String accountId,
        String customerId,
        AccountType accountType,
        Instant occurredAt) {

    public static final String TOPIC = "account.events";
    public static final int SCHEMA_VERSION = 1;
}
