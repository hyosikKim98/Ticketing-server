package com.example.ticketing.application.queue;

import com.example.ticketing.api.dto.IssuedTokenResponse;
import com.example.ticketing.api.dto.IssueQueueResponse;
import com.example.ticketing.api.dto.QueueEnterResponse;
import com.example.ticketing.api.dto.QueueMeResponse;
import com.example.ticketing.api.dto.QueueTokenResponse;
import com.example.ticketing.config.AppQueueProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueueService {
    private final StringRedisTemplate redisTemplate;
    private final AppQueueProperties queueProperties;
    private final QueueMetrics queueMetrics;

    public QueueEnterResponse enter(Long eventId, Long userId) {
        String queueKey = queueKey(eventId);
        boolean added = Boolean.TRUE.equals(redisTemplate.opsForZSet().addIfAbsent(queueKey, String.valueOf(userId), System.currentTimeMillis()));
        Long rank = redisTemplate.opsForZSet().rank(queueKey, String.valueOf(userId));

        if (rank == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to join queue");
        }

        log.info("queue.enter eventId={} userId={} added={} position={}", eventId, userId, added, rank + 1);
        queueMetrics.recordQueueEnter();
        fillAvailableSlots(eventId);
        return new QueueEnterResponse(eventId, userId, rank + 1);
    }

    public QueueMeResponse position(Long eventId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(queueKey(eventId), String.valueOf(userId));
        Long position = rank == null ? -1L : rank + 1;
        boolean hasToken = Boolean.TRUE.equals(redisTemplate.hasKey(entryTokenKey(eventId, userId)));
        return new QueueMeResponse(eventId, userId, position, hasToken);
    }

    @SuppressWarnings("unchecked")
    public IssueQueueResponse issueTopN(Long eventId, int count) {
        List<IssuedTokenResponse> issued = issueTokens(eventId, count);
        queueMetrics.recordManualIssue(issued.size());
        log.info("queue.issue.manual eventId={} requestCount={} issuedCount={}", eventId, count, issued.size());
        return new IssueQueueResponse(eventId, issued.size(), issued);
    }

    public List<IssuedTokenResponse> fillAvailableSlots(Long eventId) {
        List<IssuedTokenResponse> issued = issueTokens(eventId, queueProperties.maxActiveSlots());
        if (!issued.isEmpty()) {
            queueMetrics.recordAutoIssue(issued.size());
            log.info("queue.issue.auto eventId={} issuedCount={}", eventId, issued.size());
        }
        return issued;
    }

    public void releaseSlot(Long eventId, Long userId, String reason) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaReleaseSlotScript());
        script.setResultType(Long.class);

        Long released = redisTemplate.execute(
            script,
            List.of(activeSlotsKey(eventId), entryTokenKey(eventId, userId)),
            String.valueOf(userId)
        );

        if (released != null && released > 0) {
            queueMetrics.recordSlotRelease();
            log.info("queue.slot.released eventId={} userId={} reason={}", eventId, userId, reason);
            fillAvailableSlots(eventId);
        }
    }

    public void cleanupExpiredSlots() {
        Set<String> activeKeys = redisTemplate.keys(activeSlotsKeyPattern());
        if (activeKeys == null || activeKeys.isEmpty()) {
            return;
        }

        for (String activeKey : activeKeys) {
            Long eventId = parseEventIdFromActiveKey(activeKey);
            if (eventId == null) {
                continue;
            }

            long removedCount = pruneExpiredActiveSlots(eventId);
            if (removedCount > 0) {
                queueMetrics.recordSlotExpire(removedCount);
                log.info("queue.slot.expired eventId={} expiredCount={}", eventId, removedCount);
                fillAvailableSlots(eventId);
            }
        }
    }

    public QueueTokenResponse currentToken(Long eventId, Long userId) {
        if (!queueProperties.testTokenEndpointEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token endpoint disabled");
        }

        String key = entryTokenKey(eventId, userId);
        String token = redisTemplate.opsForValue().get(key);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry token not found");
        }

        Long expiresInSeconds = redisTemplate.getExpire(key);
        return new QueueTokenResponse(eventId, userId, token, expiresInSeconds);
    }

    @SuppressWarnings("unchecked")
    private List<IssuedTokenResponse> issueTokens(Long eventId, int requestedCount) {
        if (requestedCount <= 0) {
            return Collections.emptyList();
        }

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(luaIssueScript());
        script.setResultType(List.class);

        List<String> scriptResult = redisTemplate.execute(
            script,
            List.of(queueKey(eventId), tokenPrefix(eventId), activeSlotsKey(eventId)),
            String.valueOf(requestedCount),
            String.valueOf(queueProperties.entryTokenTtl().toSeconds()),
            String.valueOf(queueProperties.maxActiveSlots())
        );

        List<IssuedTokenResponse> issued = new ArrayList<>();
        if (scriptResult != null) {
            for (String item : scriptResult) {
                String[] parts = item.split("\\|", 2);
                if (parts.length == 2) {
                    issued.add(new IssuedTokenResponse(Long.parseLong(parts[0]), parts[1]));
                }
            }
        }
        return issued;
    }

    public boolean validateEntryToken(Long eventId, Long userId, String token) {
        String stored = redisTemplate.opsForValue().get(entryTokenKey(eventId, userId));
        return token.equals(stored);
    }

    public boolean acquirePaymentGuard(Long eventId, Long userId, String seatOption, String idempotencyKey) {
        String dedupKey = "payment_guard:" + eventId + ":" + userId + ":" + seatOption;
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
            .setIfAbsent(dedupKey, idempotencyKey, queueProperties.paymentGuardTtl()));
    }

    private String queueKey(Long eventId) {
        return "queue:" + eventId;
    }

    private String activeSlotsKey(Long eventId) {
        return "active_slots:" + eventId;
    }

    private String activeSlotsKeyPattern() {
        return "active_slots:*";
    }

    private String tokenPrefix(Long eventId) {
        return "entry_token:" + eventId + ":";
    }

    private String entryTokenKey(Long eventId, Long userId) {
        return tokenPrefix(eventId) + userId;
    }

    private Long parseEventIdFromActiveKey(String activeKey) {
        String[] parts = activeKey.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("queue.slot.cleanup.invalid-key key={}", activeKey);
            return null;
        }
    }

    private long pruneExpiredActiveSlots(Long eventId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaPruneExpiredSlotsScript());
        script.setResultType(Long.class);
        Long removed = redisTemplate.execute(
            script,
            List.of(activeSlotsKey(eventId)),
            String.valueOf(System.currentTimeMillis())
        );
        return removed == null ? 0L : removed;
    }

    private String luaIssueScript() {
        return "local queueKey = KEYS[1] "
            + "local tokenPrefix = KEYS[2] "
            + "local activeKey = KEYS[3] "
            + "local n = tonumber(ARGV[1]) "
            + "local ttl = tonumber(ARGV[2]) "
            + "local maxActive = tonumber(ARGV[3]) "
            + "local now = redis.call('TIME') "
            + "local nowMillis = now[1] * 1000 + math.floor(now[2] / 1000) "
            + "redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', nowMillis) "
            + "local currentActive = redis.call('ZCARD', activeKey) "
            + "local available = maxActive - currentActive "
            + "if available <= 0 then "
            + "return {} "
            + "end "
            + "local limit = n "
            + "if available < n then "
            + "limit = available "
            + "end "
            + "local members = redis.call('ZRANGE', queueKey, 0, limit - 1) "
            + "local out = {} "
            + "for i, userId in ipairs(members) do "
            + "local token = userId .. ':' .. now[1] .. ':' .. now[2] .. ':' .. i "
            + "local expiresAt = nowMillis + (ttl * 1000) "
            + "redis.call('SET', tokenPrefix .. userId, token, 'EX', ttl) "
            + "redis.call('ZREM', queueKey, userId) "
            + "redis.call('ZADD', activeKey, expiresAt, userId) "
            + "table.insert(out, userId .. '|' .. token) "
            + "end "
            + "return out";
    }

    private String luaReleaseSlotScript() {
        return "local activeKey = KEYS[1] "
            + "local tokenKey = KEYS[2] "
            + "local userId = ARGV[1] "
            + "local removed = redis.call('ZREM', activeKey, userId) "
            + "redis.call('DEL', tokenKey) "
            + "return removed";
    }

    private String luaPruneExpiredSlotsScript() {
        return "local activeKey = KEYS[1] "
            + "local nowMillis = tonumber(ARGV[1]) "
            + "return redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', nowMillis)";
    }
}
