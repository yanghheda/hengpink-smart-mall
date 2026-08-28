package com.hengpick.mall.identity.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.identity.domain.SmartMallTicket;
import com.hengpick.mall.identity.domain.SmartMallTicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisSmartMallTicketRepository implements SmartMallTicketRepository {
    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local payload = redis.call('GET', KEYS[1])
            if not payload then
              if redis.call('EXISTS', KEYS[2]) == 1 then return {'EXPIRED'} end
              return {'INVALID'}
            end
            local ticket = cjson.decode(payload)
            if ticket.hostType ~= ARGV[1] or ticket.deviceSessionId ~= ARGV[2]
              or ticket.h5Origin ~= ARGV[3] then return {'INVALID'} end
            redis.call('DEL', KEYS[1], KEYS[2])
            return {'CONSUMED', payload}
            """, List.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSmartMallTicketRepository(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean save(SmartMallTicket ticket, Duration ttl) {
        try {
            var ttlMillis = ttl.toMillis();
            var result = redis.execute(SAVE_SCRIPT, List.of(key(ticket.ticketHash()), markerKey(ticket.ticketHash())),
                    objectMapper.writeValueAsString(ticket), Long.toString(ttlMillis), Long.toString(ttlMillis * 2));
            return Long.valueOf(1).equals(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Ticket 元数据序列化失败", exception);
        }
    }

    @Override
    public ConsumeResult consume(String ticketHash, String hostType, String deviceSessionId, String h5Origin,
            Instant now) {
        var result = redis.execute(CONSUME_SCRIPT, List.of(key(ticketHash), markerKey(ticketHash)), hostType,
                deviceSessionId, h5Origin);
        if (result == null || result.isEmpty() || "INVALID".equals(result.getFirst())) {
            return ConsumeResult.invalid();
        }
        if ("EXPIRED".equals(result.getFirst())) {
            return ConsumeResult.expired();
        }
        try {
            var ticket = objectMapper.readValue(String.valueOf(result.get(1)), SmartMallTicket.class);
            return ticket.expiresAt().isAfter(now) ? ConsumeResult.consumed(ticket) : ConsumeResult.expired();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Ticket 元数据反序列化失败", exception);
        }
    }

    private String key(String hash) {
        return "smart-ticket:" + hash;
    }

    private String markerKey(String hash) {
        return "smart-ticket-expiry:" + hash;
    }
}
