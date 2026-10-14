package com.ridesync.tracking.config;

import com.ridesync.tracking.ws.LocationWebSocketHandler;
import com.ridesync.tracking.ws.TrackingHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final LocationWebSocketHandler handler;
    private final TrackingHandshakeInterceptor interceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/location/*")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*"); // fine for local use; restrict to your domain in production
    }
}