package com.example.ticketing.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentRequestCreateRequest(
    @NotNull Long eventId,
    @NotBlank String entryToken,
    @NotBlank @Size(max = 50) String seatOption,
    @NotNull @Min(1) Long amount,
    String idempotencyKey
) {
}
