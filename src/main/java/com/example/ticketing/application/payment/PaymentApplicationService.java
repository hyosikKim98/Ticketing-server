package com.example.ticketing.application.payment;

import com.example.ticketing.api.dto.PaymentRequestCreateRequest;
import com.example.ticketing.api.dto.PaymentRequestCreateResponse;
import com.example.ticketing.application.queue.QueueMetrics;
import com.example.ticketing.application.queue.QueueService;
import com.example.ticketing.domain.repository.PaymentRequestRepository;
import com.example.ticketing.infra.kafka.PaymentRequestCreatedEvent;
import com.example.ticketing.infra.kafka.PaymentRequestProducer;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private final QueueService queueService;
    private final QueueMetrics queueMetrics;
    private final PaymentRequestProducer producer;
    private final PaymentRequestRepository paymentRequestRepository;

    public PaymentRequestCreateResponse request(PaymentRequestCreateRequest request, Long userId) {

        if (!queueService.validateEntryToken(request.eventId(), userId, request.entryToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired entry token");
        }

        if (paymentRequestRepository.existsByUserIdAndEventIdAndSeatOption(userId, request.eventId(), request.seatOption())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment request already exists");
        }

        String idempotencyKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
            ? UUID.randomUUID().toString()
            : request.idempotencyKey();

        boolean acquired = queueService.acquirePaymentGuard(request.eventId(), userId, request.seatOption(), idempotencyKey);
        if (!acquired) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate payment request in progress");
        }

        producer.publish(
            new PaymentRequestCreatedEvent(
                idempotencyKey,
                userId,
                request.eventId(),
                request.seatOption(),
                request.amount()
            )
        );
        queueMetrics.recordPaymentPublish();

        return new PaymentRequestCreateResponse(idempotencyKey, "PUBLISHED");
    }
}
