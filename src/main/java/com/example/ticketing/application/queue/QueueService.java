package com.example.ticketing.application.queue;

import com.example.ticketing.api.dto.IssuedTokenResponse;
import com.example.ticketing.api.dto.IssueQueueResponse;
import com.example.ticketing.api.dto.QueueEnterResponse;
import com.example.ticketing.api.dto.QueueMeResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    private static final Duration ENTRY_TOKEN_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public QueueEnterResponse enter(Long eventId, Long userId) {
        String queueKey = queueKey(eventId);
        boolean added = Boolean.TRUE.equals(redisTemplate.opsForZSet().addIfAbsent(queueKey, String.valueOf(userId), System.currentTimeMillis()));
        Long rank = redisTemplate.opsForZSet().rank(queueKey, String.valueOf(userId));

        if (rank == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to join queue");
        }

        log.info("queue.enter eventId={} userId={} added={} position={}", eventId, userId, added, rank + 1);
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
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(luaIssueScript());
        script.setResultType(List.class);

        List<String> scriptResult = redisTemplate.execute(
            script,
            List.of(queueKey(eventId), tokenPrefix(eventId)),
            String.valueOf(count),
            String.valueOf(ENTRY_TOKEN_TTL.toSeconds())
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

        log.info("queue.issue eventId={} requestCount={} issuedCount={}", eventId, count, issued.size());
        return new IssueQueueResponse(eventId, issued.size(), issued);
    }

    public boolean validateEntryToken(Long eventId, Long userId, String token) {
        String stored = redisTemplate.opsForValue().get(entryTokenKey(eventId, userId));
        return token.equals(stored);
    }

    public boolean acquirePaymentGuard(Long eventId, Long userId, String seatOption, String idempotencyKey) {
        String dedupKey = "payment_guard:" + eventId + ":" + userId + ":" + seatOption;
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(dedupKey, idempotencyKey, Duration.ofMinutes(10)));
    }

    private String queueKey(Long eventId) {
        return "queue:" + eventId;
    }

    private String tokenPrefix(Long eventId) {
        return "entry_token:" + eventId + ":";
    }

    private String entryTokenKey(Long eventId, Long userId) {
        return tokenPrefix(eventId) + userId;
    }

    private String luaIssueScript() {
        return "local queueKey = KEYS[1] "
            + "local tokenPrefix = KEYS[2] "
            + "local n = tonumber(ARGV[1]) "
            + "local ttl = tonumber(ARGV[2]) "
            + "local members = redis.call('ZRANGE', queueKey, 0, n - 1) "
            + "local out = {} "
            + "for i, userId in ipairs(members) do "
            + "local now = redis.call('TIME') "
            + "local token = userId .. ':' .. now[1] .. ':' .. now[2] .. ':' .. i "
            + "redis.call('SET', tokenPrefix .. userId, token, 'EX', ttl) "
            + "redis.call('ZREM', queueKey, userId) "
            + "table.insert(out, userId .. '|' .. token) "
            + "end "
            + "return out";
    }
}
