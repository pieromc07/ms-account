package com.nttdata.msaccount.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.nttdata.msaccount.generated.dto.AccountMember;
import com.nttdata.msaccount.model.Account;
import com.nttdata.msaccount.model.AccountStatus;
import com.nttdata.msaccount.model.AccountType;
import com.nttdata.msaccount.model.Member;

@Component
public class AccountMapper {

    public com.nttdata.msaccount.generated.dto.Account toAccountDto(Account account) {
        return new com.nttdata.msaccount.generated.dto.Account()
                .id(account.getId())
                .customerId(account.getCustomerId())
                .accountType(toApiAccountType(account.getAccountType()))
                .status(toApiAccountStatus(account.getStatus()))
                .balance(toDouble(account.getBalance()))
                .createdAt(toOffsetDateTime(account.getCreatedAt()))
                .updatedAt(toOffsetDateTime(account.getUpdatedAt()));
    }

    public AccountMember toMemberDto(Member member) {
        return new AccountMember(member.customerId(), toOffsetDateTime(member.addedAt()));
    }

    public AccountType toModelAccountType(com.nttdata.msaccount.generated.dto.AccountType type) {
        return type == null ? null : AccountType.valueOf(type.name());
    }

    public AccountStatus toModelAccountStatus(com.nttdata.msaccount.generated.dto.AccountStatus status) {
        return status == null ? null : AccountStatus.valueOf(status.name());
    }

    public BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private com.nttdata.msaccount.generated.dto.AccountType toApiAccountType(AccountType type) {
        return type == null ? null : com.nttdata.msaccount.generated.dto.AccountType.valueOf(type.name());
    }

    private com.nttdata.msaccount.generated.dto.AccountStatus toApiAccountStatus(AccountStatus status) {
        return status == null ? null : com.nttdata.msaccount.generated.dto.AccountStatus.valueOf(status.name());
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
