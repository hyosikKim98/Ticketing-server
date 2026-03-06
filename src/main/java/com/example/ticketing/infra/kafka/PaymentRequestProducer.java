package com.example.ticketing.infra.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentRequestProducer {

    private static final String TOPIC = "payment-requests";

    private final KafkaTemplate<String, PaymentRequestCreatedEvent> kafkaTemplate;

    public void publish(PaymentRequestCreatedEvent event) {
        log.info("payment.produce idempotencyKey={} eventId={} userId={}", event.idempotencyKey(), event.eventId(), event.userId());
        kafkaTemplate.send(TOPIC, event.idempotencyKey(), event);
    }
}
