package com.nttdata.msaccount.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("debit_cards")
public class IssuedDebitCard {

    @Id
    private String id;
    private String accountId;
    private String customerId;
    private DebitCardStatus status;
    private Instant createdAt;
}
