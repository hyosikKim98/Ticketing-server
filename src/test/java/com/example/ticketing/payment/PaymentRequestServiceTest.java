package com.example.ticketing.payment;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ticketing.application.inventory.TicketInventoryService;
import com.example.ticketing.application.payment.PaymentRequestService;
import com.example.ticketing.application.queue.QueueMetrics;
import com.example.ticketing.application.queue.QueueService;
import com.example.ticketing.domain.repository.PaymentRequestRepository;
import com.example.ticketing.infra.kafka.PaymentRequestCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceTest {

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private TicketInventoryService ticketInventoryService;

    @Mock
    private QueueService queueService;

    @Mock
    private QueueMetrics queueMetrics;

    @InjectMocks
    private PaymentRequestService paymentRequestService;

    @Test
    void skipInventoryWhenDuplicateInsertReturnsZero() {
        PaymentRequestCreatedEvent event = new PaymentRequestCreatedEvent("idem-dup", 1L, 10L, "VIP", 1000L);
        when(paymentRequestRepository.insertIfAbsent(anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyString()))
            .thenReturn(0);

        paymentRequestService.recordFromEvent(event);

        verify(paymentRequestRepository, times(1))
            .insertIfAbsent(1L, 10L, "VIP", 1000L, "REQUESTED", "idem-dup");
        verify(ticketInventoryService, never()).reserveOne(anyLong());
        verify(queueService, never()).releaseSlot(anyLong(), anyLong(), anyString());
    }

    @Test
    void reserveInventoryWhenInsertSucceeded() {
        PaymentRequestCreatedEvent event = new PaymentRequestCreatedEvent("idem-ok", 2L, 20L, "R", 2000L);
        when(paymentRequestRepository.insertIfAbsent(anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyString()))
            .thenReturn(1);

        paymentRequestService.recordFromEvent(event);

        verify(ticketInventoryService, times(1)).reserveOne(20L);
        verify(queueService, times(1)).releaseSlot(20L, 2L, "PAYMENT_RECORDED");
    }
}
