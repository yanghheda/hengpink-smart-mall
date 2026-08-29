package com.hengpick.mall.decision.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.decision.event.DecisionStreamEvent;
import com.hengpick.mall.decision.event.DecisionStreamStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 使用 Redis Stream 保存一小时内的非权威进度通知。 */
public final class RedisDecisionStreamStore implements DecisionStreamStore {
    private static final Duration TTL = Duration.ofHours(1);
    private static final DefaultRedisScript<String> APPEND_SCRIPT = new DefaultRedisScript<>("""
            local seen = 'seen:' .. ARGV[1]
            if redis.call('HEXISTS', KEYS[2], seen) == 1 then return '' end
            local current = tonumber(redis.call('HGET', KEYS[2], 'progress') or '-1')
            local incoming = tonumber(ARGV[2])
            if incoming < current then return '' end
            local id = redis.call('XADD', KEYS[1], '*',
              'eventType', ARGV[3], 'occurredAt', ARGV[4], 'sessionId', ARGV[5],
              'runId', ARGV[6], 'runVersion', ARGV[7], 'progress', ARGV[2], 'payload', ARGV[8])
            redis.call('HSET', KEYS[2], seen, '1', 'progress', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[9])
            redis.call('EXPIRE', KEYS[2], ARGV[9])
            return id
            """, String.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisDecisionStreamStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean append(DecisionStreamEvent event, String dedupeKey) {
        try {
            var id = redis.execute(APPEND_SCRIPT, List.of(streamKey(event.runId()), stateKey(event.runId())),
                    dedupeKey, Integer.toString(event.progress()), event.eventType(), event.occurredAt().toString(),
                    event.sessionId(), event.runId(), Integer.toString(event.runVersion()),
                    objectMapper.writeValueAsString(event.payload()), Long.toString(TTL.toSeconds()));
            return id != null && !id.isBlank();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("决策事件无法序列化", exception);
        }
    }

    @Override
    public String latestId(String runId) {
        var records = redis.opsForStream().reverseRange(streamKey(runId), Range.unbounded(), Limit.limit().count(1));
        return records == null || records.isEmpty() ? "0-0" : records.getFirst().getId().getValue();
    }

    @Override
    public List<DecisionStreamEvent> readAfter(String runId, String lastEventId, int count) {
        var records = redis.opsForStream().range(streamKey(runId), Range.rightOpen(lastEventId, "+"),
                Limit.limit().count(count));
        if (records == null) return List.of();
        return records.stream().map(record -> fromRecord(record.getId().getValue(), record.getValue())).toList();
    }

    private DecisionStreamEvent fromRecord(String id, Map<Object, Object> fields) {
        try {
            @SuppressWarnings("unchecked")
            var payload = objectMapper.readValue(String.valueOf(fields.get("payload")), Map.class);
            return new DecisionStreamEvent(id, String.valueOf(fields.get("eventType")),
                    Instant.parse(String.valueOf(fields.get("occurredAt"))), String.valueOf(fields.get("sessionId")),
                    String.valueOf(fields.get("runId")), Integer.parseInt(String.valueOf(fields.get("runVersion"))),
                    Integer.parseInt(String.valueOf(fields.get("progress"))), payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis 决策事件格式无效", exception);
        }
    }

    private String streamKey(String runId) {
        return "decision:events:" + runId;
    }

    private String stateKey(String runId) {
        return "decision:event-state:" + runId;
    }
}
