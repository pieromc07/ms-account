package com.nttdata.msaccount.service;

import com.nttdata.msaccount.event.BalanceResult;

/**
 * Result of applying one balance change. The account id is null when no account could be resolved.
 */
public record Movement(String accountId, BalanceResult.Outcome outcome, String reason) {

    public static Movement ok(String accountId) {
        return new Movement(accountId, BalanceResult.Outcome.OK, null);
    }

    public static Movement rejected(String accountId, String reason) {
        return new Movement(accountId, BalanceResult.Outcome.REJECTED, reason);
    }
}
