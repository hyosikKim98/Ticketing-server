package com.example.ticketing.api.dto;

public record PaymentRequestCreateResponse(String idempotencyKey, String status) {
}
