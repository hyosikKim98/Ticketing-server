package com.example.ticketing.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ticketing.api.dto.QueueEnterResponse;
import com.example.ticketing.application.queue.QueueService;
import com.example.ticketing.application.queue.QueueMetrics;
import com.example.ticketing.config.AppQueueProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class QueueServiceTest {

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSetOperations;
    private QueueService queueService;
    private QueueMetrics queueMetrics;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        queueMetrics = mock(QueueMetrics.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        queueService = new QueueService(
            redisTemplate,
            new AppQueueProperties(Duration.ofMinutes(10), Duration.ofMinutes(10), 20, false),
            queueMetrics
        );
    }

    @Test
    void duplicateEnterKeepsStablePosition() {
        when(zSetOperations.addIfAbsent(
            org.mockito.ArgumentMatchers.eq("queue:10"),
            org.mockito.ArgumentMatchers.eq("99"),
            org.mockito.ArgumentMatchers.anyDouble())
        ).thenReturn(true, false);
        when(zSetOperations.rank("queue:10", "99")).thenReturn(0L);

        QueueEnterResponse first = queueService.enter(10L, 99L);
        QueueEnterResponse second = queueService.enter(10L, 99L);

        assertThat(first.position()).isEqualTo(1L);
        assertThat(second.position()).isEqualTo(1L);
    }
}
