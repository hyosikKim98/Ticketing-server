package com.example.ticketing.api.dto;

public record QueueMeResponse(Long eventId, Long userId, Long position, boolean hasEntryToken) {
}
