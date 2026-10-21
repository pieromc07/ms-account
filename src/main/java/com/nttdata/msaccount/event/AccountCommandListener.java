package com.nttdata.msaccount.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.nttdata.msaccount.service.AccountCommandHandler;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka edge for balance and debit-card commands. Failures are rethrown so Kafka redelivers the record.
 */
@Component
@RequiredArgsConstructor
public class AccountCommandListener {

    private final AccountCommandHandler handler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = BalanceCommand.TOPIC, groupId = "ms-account-balance-commands")
    public void onBalanceCommand(String payload) {
        BalanceCommand command = objectMapper.readValue(payload, BalanceCommand.class);
        handler.handleBalance(command).blockingAwait();
    }

    @KafkaListener(topics = DebitCardCharge.TOPIC, groupId = "ms-account-debit-commands")
    public void onDebitCardCharge(String payload) {
        DebitCardCharge charge = objectMapper.readValue(payload, DebitCardCharge.class);
        handler.handleDebitCharge(charge).blockingAwait();
    }
}
