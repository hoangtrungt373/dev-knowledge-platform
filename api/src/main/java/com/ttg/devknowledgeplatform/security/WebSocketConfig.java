package com.ttg.devknowledgeplatform.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.ttg.devknowledgeplatform.api.impl.DmMessagingController;
import com.ttg.devknowledgeplatform.api.impl.GroupMessagingController;
import com.ttg.devknowledgeplatform.config.web.CurrentUserIdMessageArgumentResolver;

import lombok.RequiredArgsConstructor;

/**
 * STOMP-over-WebSocket wiring for live group/DM chat push — the WebSocket-transport counterpart to
 * {@link SecurityConfig}, which is why it lives alongside it here rather than in a general
 * {@code config} package. The AI chat feature keeps its existing SSE stream — this is unrelated,
 * additive infrastructure for the social chat feature only.
 *
 * <p>Destinations: {@code /app/**} — client-sent, routed to {@code @MessageMapping} handlers
 * ({@link GroupMessagingController}, {@link DmMessagingController}); {@code /topic/channels/{id}}
 * — broadcast channel messages, subscription gated by {@link StompAuthChannelInterceptor};
 * {@code /user/queue/dms} — private per-user DM delivery via
 * {@code SimpMessagingTemplate#convertAndSendToUser}, no public topic string involved so no
 * separate subscribe-time check is needed there.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final CurrentUserIdMessageArgumentResolver currentUserIdMessageArgumentResolver;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // No SockJS fallback: a raw WebSocket handshake (101 Switching Protocols), not an
        // emulated transport — matches the mechanics this feature is meant to demonstrate.
        registry.addEndpoint("/ws").setAllowedOrigins(frontendUrl);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        // User-destination prefix defaults to "/user" — convertAndSendToUser relies on that default.
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(currentUserIdMessageArgumentResolver);
    }
}
