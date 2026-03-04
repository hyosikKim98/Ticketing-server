package com.example.ticketing.api.dto;

import java.util.List;

public record IssueQueueResponse(Long eventId, int issuedCount, List<IssuedTokenResponse> issued) {
}
