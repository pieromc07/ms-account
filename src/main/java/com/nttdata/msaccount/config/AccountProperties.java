package com.nttdata.msaccount.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account")
public record AccountProperties(Rules rules, Clients clients) {

    public record Rules(BigDecimal minimumOpeningAmount) {
    }

    public record Clients(String customerBaseUrl, String creditBaseUrl, Duration timeout) {
    }
}
