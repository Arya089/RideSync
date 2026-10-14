package com.ridesync.tracking.ws;

import com.ridesync.ride.entity.Ride;
import com.ridesync.ride.repository.RideRepository;
import com.ridesync.tracking.service.TrackingAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class LocationWebSocketHandler extends TextWebSocketHandler {

    private static final Duration LOCATION_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final RideRepository rideRepository;

    // rideId -> connected sessions (in-memory, single instance)
    private final Map<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long rideId = rideId(session);
        rooms.computeIfAbsent(rideId, id -> ConcurrentHashMap.newKeySet()).add(session);

        // Late joiner: immediately send the driver's last known position from Redis.
        if (isViewer(session)) {
            String last = redis.opsForValue().get(TrackingAccessService.locationKey(rideId));
            if (last != null) {
                send(session, last);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!isDriver(session)) {
            send(session, error("only the driver can send locations"));
            return;
        }
        Long rideId = rideId(session);
        if (!rideRepository.existsByIdAndStatus(rideId, Ride.Status.STARTED)) {
            send(session, error("ride is not in progress"));
            return;
        }

        double lat;
        double lng;
        try {
            Map<String, Object> body = JsonParserFactory.getJsonParser().parseMap(message.getPayload());
            lat = number(body.get("lat"));
            lng = number(body.get("lng"));
        } catch (RuntimeException e) {
            send(session, error("invalid message, send lat and lng as numbers"));
            return;
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            send(session, error("lat or lng out of range"));
            return;
        }

        String json = String.format(Locale.ROOT,
                "{\"rideId\":%d,\"lat\":%.6f,\"lng\":%.6f,\"ts\":%d}",
                rideId, lat, lng, System.currentTimeMillis());

        redis.opsForValue().set(TrackingAccessService.locationKey(rideId), json, LOCATION_TTL);

        Set<WebSocketSession> room = rooms.get(rideId);
        if (room != null) {
            for (WebSocketSession s : room) {
                if (isViewer(s)) {
                    send(s, json);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long rideId = rideId(session);
        Set<WebSocketSession> room = rooms.get(rideId);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) {
                rooms.remove(rideId);
            }
        }
    }

    private static double number(Object value) {
        if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("not a number");
    }

    private static String error(String message) {
        return "{\"error\":\"" + message + "\"}";
    }

    // WebSocketSession.sendMessage is not thread-safe, so serialise writes per session.
    private void send(WebSocketSession session, String text) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(text));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private static Long rideId(WebSocketSession session) {
        return (Long) session.getAttributes().get("rideId");
    }

    private static boolean isDriver(WebSocketSession session) {
        return "DRIVER".equals(session.getAttributes().get("role"));
    }

    private static boolean isViewer(WebSocketSession session) {
        return "VIEWER".equals(session.getAttributes().get("role"));
    }
}