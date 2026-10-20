package com.nttdata.msaccount.model;

import java.math.BigDecimal;
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
@Document("account_products")
public class AccountProduct {

    @Id
    private String code;
    private String name;
    private AccountType accountType;

    @Builder.Default
    private List<CustomerType> customerTypes = new ArrayList<>();

    @Builder.Default
    private List<CustomerProfile> customerProfiles = new ArrayList<>();

    private boolean requiresCreditCard;
    private BigDecimal minimumOpeningAmount;
}
