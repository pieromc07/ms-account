package com.nttdata.msaccount.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
@Document("accounts")
public class Account {

    @Id
    private String id;
    private String customerId;
    private CustomerType customerType;
    private AccountType accountType;
    private AccountStatus status;
    private BigDecimal balance;

    @Builder.Default
    private List<Member> holders = new ArrayList<>();

    @Builder.Default
    private List<Member> signers = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;
}
