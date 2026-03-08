package com.example.ticketing.application.queue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class QueueMetrics {

    private final StringRedisTemplate redisTemplate;
    private final Counter queueEnterCounter;
    private final Counter autoIssueCounter;
    private final Counter manualIssueCounter;
    private final Counter slotReleaseCounter;
    private final Counter slotExpireCounter;
    private final Counter paymentPublishCounter;
    private final Counter paymentDuplicateCounter;

    public QueueMetrics(MeterRegistry meterRegistry, StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.queueEnterCounter = meterRegistry.counter("ticketing.queue.enter.total");
        this.autoIssueCounter = meterRegistry.counter("ticketing.queue.issue.auto.total");
        this.manualIssueCounter = meterRegistry.counter("ticketing.queue.issue.manual.total");
        this.slotReleaseCounter = meterRegistry.counter("ticketing.queue.slot.release.total");
        this.slotExpireCounter = meterRegistry.counter("ticketing.queue.slot.expire.total");
        this.paymentPublishCounter = meterRegistry.counter("ticketing.payment.publish.total");
        this.paymentDuplicateCounter = meterRegistry.counter("ticketing.payment.duplicate.total");

        Gauge.builder("ticketing.queue.active.slots", this, QueueMetrics::activeSlots)
            .description("Current active entry-token slots across all events")
            .register(meterRegistry);
        Gauge.builder("ticketing.queue.waiting.users", this, QueueMetrics::waitingUsers)
            .description("Current waiting queue users across all events")
            .register(meterRegistry);
    }

    public void recordQueueEnter() {
        queueEnterCounter.increment();
    }

    public void recordAutoIssue(int issuedCount) {
        if (issuedCount > 0) {
            autoIssueCounter.increment(issuedCount);
        }
    }

    public void recordManualIssue(int issuedCount) {
        if (issuedCount > 0) {
            manualIssueCounter.increment(issuedCount);
        }
    }

    public void recordSlotRelease() {
        slotReleaseCounter.increment();
    }

    public void recordSlotExpire(long expiredCount) {
        if (expiredCount > 0) {
            slotExpireCounter.increment(expiredCount);
        }
    }

    public void recordPaymentPublish() {
        paymentPublishCounter.increment();
    }

    public void recordPaymentDuplicate() {
        paymentDuplicateCounter.increment();
    }

    private double activeSlots() {
        return countSortedSetMembers("active_slots:*");
    }

    private double waitingUsers() {
        return countSortedSetMembers("queue:*");
    }

    private double countSortedSetMembers(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0D;
        }

        long total = 0L;
        for (String key : keys) {
            Long count = redisTemplate.opsForZSet().zCard(key);
            total += count == null ? 0L : count;
        }
        return total;
    }
}
