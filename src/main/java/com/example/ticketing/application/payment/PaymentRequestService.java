package com.example.ticketing.application.payment;

import com.example.ticketing.application.inventory.TicketInventoryService;
import com.example.ticketing.application.queue.QueueMetrics;
import com.example.ticketing.application.queue.QueueService;
import com.example.ticketing.domain.repository.PaymentRequestRepository;
import com.example.ticketing.infra.kafka.PaymentRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRequestService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final TicketInventoryService ticketInventoryService;
    private final QueueService queueService;
    private final QueueMetrics queueMetrics;

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
            queueMetrics.recordPaymentDuplicate();
            log.info("payment.consume.skip-duplicate idempotencyKey={}", event.idempotencyKey());
            return;
        }

        ticketInventoryService.reserveOne(event.eventId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queueService.releaseSlot(event.eventId(), event.userId(), "PAYMENT_RECORDED");
                }
            });
        } else {
            queueService.releaseSlot(event.eventId(), event.userId(), "PAYMENT_RECORDED");
        }
        log.info("payment.consume.persisted idempotencyKey={} eventId={} userId={}",
            event.idempotencyKey(), event.eventId(), event.userId());
    }
}
