package com.nttdata.msaccount.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.nttdata.msaccount.service.CreditProjectionService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka edge for ms-credit events that feed the local card and overdue projections.
 */
@Component
@RequiredArgsConstructor
public class CreditEventListener {

    private final CreditProjectionService projections;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = CreditCardEvent.TOPIC, groupId = "ms-account-credit-card-events")
    public void onCreditCardEvent(String payload) {
        CreditCardEvent event = objectMapper.readValue(payload, CreditCardEvent.class);
        projections.applyCardEvent(event).blockingAwait();
    }

    @KafkaListener(topics = OverdueDebtEvent.TOPIC, groupId = "ms-account-overdue-debt-events")
    public void onOverdueDebtEvent(String payload) {
        OverdueDebtEvent event = objectMapper.readValue(payload, OverdueDebtEvent.class);
        projections.applyOverdueEvent(event).blockingAwait();
    }
}
