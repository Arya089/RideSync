package com.ridesync.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatLockService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration MAX_WAIT = Duration.ofSeconds(3);

    // Delete the key only if it still holds OUR token (atomic check-and-delete).
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    /** Returns a lock token, or null if the lock could not be acquired in time. */
    public String acquire(Long rideId) {
        String key = key(rideId);
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                // SET key token NX EX 10  -> atomic "create only if absent, with expiry"
                Boolean ok = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
                if (Boolean.TRUE.equals(ok)) {
                    return token;
                }
                Thread.sleep(25);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public void release(Long rideId, String token) {
        redis.execute(UNLOCK_SCRIPT, List.of(key(rideId)), token);
    }

    private String key(Long rideId) {
        return "booking:lock:" + rideId;
    }
}