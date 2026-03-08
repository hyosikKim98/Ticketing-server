package com.example.ticketing.infra.kafka;

import com.example.ticketing.application.payment.PaymentRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@EnableKafka
public class PaymentRequestKafkaConsumer {

    private final PaymentRequestService paymentRequestService;

    @KafkaListener(topics = "payment-requests", groupId = "ticketing-payment", containerFactory = "paymentKafkaListenerContainerFactory")
    public void listen(PaymentRequestCreatedEvent event, Acknowledgment acknowledgment) {
        log.info("payment.consume.received idempotencyKey={} eventId={} userId={}", event.idempotencyKey(), event.eventId(), event.userId());
        paymentRequestService.recordFromEvent(event);
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
