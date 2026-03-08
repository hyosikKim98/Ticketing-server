package com.example.ticketing.application.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class QueueSlotScheduler {

    private final QueueService queueService;

    @Scheduled(fixedDelay = 30_000L)
    public void cleanupExpiredSlots() {
        log.debug("queue.slot.cleanup.start");
        queueService.cleanupExpiredSlots();
    }
}
