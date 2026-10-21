package com.nttdata.msaccount.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.nttdata.msaccount.event.BalanceResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of a balance command, keyed by transaction id so redelivered commands republish the same reply.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("processed_balance_commands")
public class ProcessedBalanceCommand {

    @Id
    private String transactionId;
    private String accountId;
    private BalanceResult.Outcome outcome;
    private String reason;
    private Instant processedAt;
}
