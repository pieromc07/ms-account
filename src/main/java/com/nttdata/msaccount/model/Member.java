package com.nttdata.msaccount.model;

import java.time.Instant;

public record Member(String customerId, Instant addedAt) {
}
