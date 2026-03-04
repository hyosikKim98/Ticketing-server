package com.example.ticketing.api.dto;

import java.time.Instant;

public record EventResponse(
    Long id,
    String name,
    String venue,
    Instant startsAt
) {
}
