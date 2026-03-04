package com.example.ticketing.application.payment;

import com.example.ticketing.application.inventory.TicketInventoryService;
import com.example.ticketing.domain.entity.PaymentRequest;
import com.example.ticketing.domain.entity.PaymentStatus;
import com.example.ticketing.domain.repository.PaymentRequestRepository;
import com.example.ticketing.infra.kafka.PaymentRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentRequestService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestService.class);

    private final PaymentRequestRepository paymentRequestRepository;
    private final TicketInventoryService ticketInventoryService;

    @Transactional
    public void recordFromEvent(PaymentRequestCreatedEvent event) {
        if (paymentRequestRepository.existsByIdempotencyKey(event.idempotencyKey())) {
            log.info("payment.consume.skip-duplicate idempotencyKey={}", event.idempotencyKey());
            return;
        }

        ticketInventoryService.reserveOne(event.eventId());

        try {
            paymentRequestRepository.save(
                new PaymentRequest(
                    event.userId(),
                    event.eventId(),
                    event.seatOption(),
                    event.amount(),
                    PaymentStatus.REQUESTED,
                    event.idempotencyKey()
                )
            );
            log.info("payment.consume.persisted idempotencyKey={} eventId={} userId={}", event.idempotencyKey(), event.eventId(), event.userId());
        } catch (DataIntegrityViolationException e) {
            log.info("payment.consume.unique-conflict idempotencyKey={}", event.idempotencyKey());
        }
    }
}
