package com.example.ticketing.api.queue;

import com.example.ticketing.api.dto.IssueQueueRequest;
import com.example.ticketing.api.dto.IssueQueueResponse;
import com.example.ticketing.api.dto.QueueEnterResponse;
import com.example.ticketing.api.dto.QueueMeResponse;
import com.example.ticketing.api.dto.QueueTokenResponse;
import com.example.ticketing.application.queue.QueueService;
import com.example.ticketing.security.CustomPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/{eventId}/enter")
    public QueueEnterResponse enter(@PathVariable Long eventId, @AuthenticationPrincipal CustomPrincipal principal) {
        return queueService.enter(eventId, principal.userId());
    }

    @GetMapping("/{eventId}/me")
    public QueueMeResponse me(@PathVariable Long eventId,  @AuthenticationPrincipal CustomPrincipal principal) {
        return queueService.position(eventId, principal.userId());
    }

    @GetMapping("/{eventId}/token")
    public QueueTokenResponse token(@PathVariable Long eventId, @AuthenticationPrincipal CustomPrincipal principal) {
        return queueService.currentToken(eventId, principal.userId());
    }

    @PostMapping("/{eventId}/issue")
    public IssueQueueResponse issue(@PathVariable Long eventId, @Valid @RequestBody IssueQueueRequest request) {
        return queueService.issueTopN(eventId, request.count());
    }
}
