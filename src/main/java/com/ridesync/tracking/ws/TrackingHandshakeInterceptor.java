package com.ridesync.tracking.ws;

import com.ridesync.auth.security.JwtService;
import com.ridesync.tracking.service.TrackingAccessService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/** Authenticates the WebSocket upgrade: JWT from ?token=, then ride-level access check. */
@Component
@RequiredArgsConstructor
public class TrackingHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final TrackingAccessService accessService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            URI uri = request.getURI();
            String path = uri.getPath();
            Long rideId = Long.valueOf(path.substring(path.lastIndexOf('/') + 1));

            String token = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
            if (token == null || token.isBlank()) {
                return reject(response, HttpStatus.UNAUTHORIZED);
            }
            String email = jwtService.extractEmail(token);

            TrackingAccessService.Role role = accessService.roleFor(email, rideId);
            if (role == TrackingAccessService.Role.NONE) {
                return reject(response, HttpStatus.FORBIDDEN);
            }
            attributes.put("rideId", rideId);
            attributes.put("role", role.name());
            attributes.put("email", email);
            return true;
        } catch (NumberFormatException e) {
            return reject(response, HttpStatus.BAD_REQUEST);
        } catch (JwtException | IllegalArgumentException e) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }

    private boolean reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }
}