package com.nttdata.msaccount.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.CustomerType;
import com.nttdata.msaccount.model.Member;

class AccountMapperTest {

    private final AccountMapper mapper = new AccountMapper();

    @Test
    void toAccountDtoMapsAllFields() {
        Instant created = Instant.parse("2025-01-10T10:15:30Z");
        Instant updated = Instant.parse("2025-01-11T08:00:00Z");
        Account account = Account.builder()
                .id("64b000000000000000000001")
                .customerId("64b000000000000000000002")
                .customerType(CustomerType.BUSINESS)
                .accountType(AccountType.CURRENT)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("150.50"))
                .holders(new ArrayList<>())
                .signers(new ArrayList<>())
                .createdAt(created)
                .updatedAt(updated)
                .build();

        com.nttdata.msaccount.generated.dto.Account dto = mapper.toAccountDto(account);

        assertThat(dto.getId()).isEqualTo("64b000000000000000000001");
        assertThat(dto.getCustomerId()).isEqualTo("64b000000000000000000002");
        assertThat(dto.getAccountType()).isEqualTo(com.nttdata.msaccount.generated.dto.AccountType.CURRENT);
        assertThat(dto.getStatus()).isEqualTo(com.nttdata.msaccount.generated.dto.AccountStatus.ACTIVE);
        assertThat(dto.getBalance()).isEqualTo(150.5d);
        assertThat(dto.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(created, ZoneOffset.UTC));
        assertThat(dto.getUpdatedAt()).isEqualTo(OffsetDateTime.ofInstant(updated, ZoneOffset.UTC));
        assertThat(dto.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void toAccountDtoHandlesMissingOptionalValues() {
        Account account = Account.builder()
                .id("64b000000000000000000001")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.INACTIVE)
                .build();

        com.nttdata.msaccount.generated.dto.Account dto = mapper.toAccountDto(account);

        assertThat(dto.getBalance()).isNull();
        assertThat(dto.getCreatedAt()).isNull();
        assertThat(dto.getUpdatedAt()).isNull();
        assertThat(dto.getStatus()).isEqualTo(com.nttdata.msaccount.generated.dto.AccountStatus.INACTIVE);
    }

    @Test
    void toMemberDtoMapsCustomerAndTimestampInUtc() {
        Instant addedAt = Instant.parse("2025-02-01T12:00:00Z");

        var dto = mapper.toMemberDto(new Member("64b000000000000000000003", addedAt));

        assertThat(dto.getCustomerId()).isEqualTo("64b000000000000000000003");
        assertThat(dto.getAddedAt()).isEqualTo(OffsetDateTime.ofInstant(addedAt, ZoneOffset.UTC));
    }

    @Test
    void accountTypeRoundTripsBetweenApiAndModel() {
        for (com.nttdata.msaccount.generated.dto.AccountType apiType
                : com.nttdata.msaccount.generated.dto.AccountType.values()) {
            AccountType modelType = mapper.toModelAccountType(apiType);
            assertThat(modelType.name()).isEqualTo(apiType.name());
        }
    }

    @Test
    void accountStatusRoundTripsBetweenApiAndModel() {
        for (com.nttdata.msaccount.generated.dto.AccountStatus apiStatus
                : com.nttdata.msaccount.generated.dto.AccountStatus.values()) {
            AccountStatus modelStatus = mapper.toModelAccountStatus(apiStatus);
            assertThat(modelStatus.name()).isEqualTo(apiStatus.name());
        }
    }

    @Test
    void nullEnumsMapToNull() {
        assertThat(mapper.toModelAccountType(null)).isNull();
        assertThat(mapper.toModelAccountStatus(null)).isNull();
    }

    @Test
    void toBigDecimalConvertsDoubleAndKeepsNullAsNull() {
        assertThat(mapper.toBigDecimal(12.5d)).isEqualByComparingTo("12.5");
        assertThat(mapper.toBigDecimal(null)).isNull();
    }

    @Test
    void toBigDecimalAvoidsBinaryRepresentationNoise() {
        assertThat(mapper.toBigDecimal(0.1d)).isEqualTo(new BigDecimal("0.1"));
    }

}
