package com.bank.account.event;

import com.bank.account.dto.AccountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes account events to Kafka. Failures are logged, never thrown. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    public static final String TOPIC = "account-events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Format: CREATED|accountNumber|customerDocument|accountType. */
    public void accountCreated(AccountDto account) {
        send(account.getAccountNumber(), String.join("|", "CREATED",
                account.getAccountNumber(), account.getCustomerDocument(),
                account.getAccountType().name()));
    }

    /** Format: DELETED|accountNumber. */
    public void accountDeleted(String accountNumber) {
        send(accountNumber, "DELETED|" + accountNumber);
    }

    private void send(String key, String message) {
        try {
            kafkaTemplate.send(TOPIC, key, message).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Could not publish event {}: {}", message, error.toString());
                } else {
                    log.info("Event published to {}: {}", TOPIC, message);
                }
            });
        } catch (Exception e) {
            log.error("Could not publish event {}: {}", message, e.toString());
        }
    }
}