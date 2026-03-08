package com.example.ticketing.api.dto;

public record QueueTokenResponse(Long eventId, Long userId, String entryToken, Long expiresInSeconds) {
}
