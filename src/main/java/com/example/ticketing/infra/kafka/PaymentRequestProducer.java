package com.example.ticketing.infra.kafka;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRequestProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestProducer.class);
    private static final String TOPIC = "payment-requests";

    private final KafkaTemplate<String, PaymentRequestCreatedEvent> kafkaTemplate;

    public void publish(PaymentRequestCreatedEvent event) {
        log.info("payment.produce idempotencyKey={} eventId={} userId={}", event.idempotencyKey(), event.eventId(), event.userId());
        kafkaTemplate.send(TOPIC, event.idempotencyKey(), event);
    }
}
