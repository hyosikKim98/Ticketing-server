package com.example.ticketing.application.payment;

import com.example.ticketing.application.inventory.TicketInventoryService;
import com.example.ticketing.domain.repository.PaymentRequestRepository;
import com.example.ticketing.infra.kafka.PaymentRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRequestService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final TicketInventoryService ticketInventoryService;

    @Transactional
    public void recordFromEvent(PaymentRequestCreatedEvent event) {
        int inserted = paymentRequestRepository.insertIfAbsent(
               event.userId(),
               event.eventId(),
               event.seatOption(),
               event.amount(),
               "REQUESTED",
               event.idempotencyKey()
        );

        if (inserted == 0) {
            log.info("payment.consume.skip-duplicate idempotencyKey={}", event.idempotencyKey());
            return;
        }

        ticketInventoryService.reserveOne(event.eventId());
        log.info("payment.consume.persisted idempotencyKey={} eventId={} userId={}",
            event.idempotencyKey(), event.eventId(), event.userId());
    }
}
