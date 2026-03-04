package com.example.ticketing.infra.kafka;

public record PaymentRequestCreatedEvent(
    String idempotencyKey,
    Long userId,
    Long eventId,
    String seatOption,
    Long amount
) {
}
