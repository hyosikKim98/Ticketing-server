package com.example.ticketing.api.dto;

import jakarta.validation.constraints.Min;

public record IssueQueueRequest(@Min(1) int count) {
}
