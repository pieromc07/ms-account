package com.nttdata.msaccount.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account.catalog")
public record CatalogProperties(Duration cacheTtl) {
}
