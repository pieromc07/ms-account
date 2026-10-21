package com.nttdata.msaccount.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local projection of whether a customer has overdue credit debt, built from ms-credit events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("overdue_debt_projections")
public class OverdueDebtProjection {

    @Id
    private String customerId;
    private boolean overdue;
    private Instant occurredAt;
}
