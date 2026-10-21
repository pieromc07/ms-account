package com.nttdata.msaccount.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local projection of a credit card's lifecycle, built from ms-credit events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("credit_card_projections")
public class CreditCardProjection {

    @Id
    private String creditCardId;
    private String customerId;
    private boolean active;
    private Instant occurredAt;
}
