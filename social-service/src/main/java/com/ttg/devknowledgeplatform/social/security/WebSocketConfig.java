package com.ttg.devknowledgeplatform.social.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.ttg.devknowledgeplatform.social.api.impl.DmMessagingController;
import com.ttg.devknowledgeplatform.social.api.impl.GroupMessagingController;
import com.ttg.devknowledgeplatform.social.config.web.CurrentUserIdMessageArgumentResolver;

import lombok.RequiredArgsConstructor;

/**
 * STOMP-over-WebSocket wiring for live group/DM chat push.
 *
 * <p>Duplicated from {@code gateway}'s class of the same name — relocated here as part of this
 * module's standalone extraction. {@code gateway} had no other use for WebSocket/STOMP transport
 * (this feature was the only one), so nothing remains there now; this module owns its own
 * {@code /ws} endpoint, own port, entirely independent of {@code gateway}'s REST-only transport.
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
