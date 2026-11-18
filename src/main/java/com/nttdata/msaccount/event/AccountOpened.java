package com.nttdata.msaccount.event;

import java.time.Instant;

import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerProfile;

public record AccountOpened(
        int schemaVersion,
        String accountId,
        String customerId,
        AccountType accountType,
        CustomerProfile customerProfile,
        Instant occurredAt) {

    public static final String TOPIC = "account.events";
    public static final int SCHEMA_VERSION = 2;
}
