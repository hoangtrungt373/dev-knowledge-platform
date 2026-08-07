package com.ttg.devknowledgeplatform.social.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ttg.devknowledgeplatform.social.repository.SocialProfileRepository;
import com.ttg.devknowledgeplatform.social.service.GroupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates STOMP {@code CONNECT} frames and authorizes {@code SUBSCRIBE} frames to channel
 * topics.
 *
 * <p>Duplicated from {@code gateway}'s class of the same name — relocated here now that this
 * module is a standalone app owning its own WebSocket/STOMP transport (see
 * {@link WebSocketConfig}); {@code gateway} never had a second use for STOMP, so nothing remains
 * there once this module was extracted.
 *
 * <p><b>CONNECT:</b> the WebSocket HTTP handshake itself is {@code permitAll} in
 * {@link SecurityConfig} — browsers can't set an {@code Authorization} header on the handshake
 * request, so real authentication happens here instead, on the first STOMP frame the client sends
 * after the socket opens (which, unlike the handshake, can carry arbitrary headers). Reuses
 * {@link KeycloakJwtAuthenticationConverter} — the same JIT-provisioning/principal-building path
 * the REST filter chain uses — so downstream code (e.g. {@link CurrentUserResolver}) doesn't need
 * a second principal shape to handle.
 *
 * <p><b>SUBSCRIBE:</b> the simple in-memory broker has no per-destination ACL — anyone who knows a
 * topic string could otherwise subscribe to {@code /topic/channels/{id}} for a channel they're not
 * a member of. This checks {@link GroupService#isChannelMember} before admitting the subscription.
 * DM delivery doesn't need an equivalent check: it uses {@code convertAndSendToUser}'s private
 * per-user queue, which has no public topic string to subscribe to in the first place.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CHANNEL_TOPIC = Pattern.compile("^/topic/channels/(\\d+)$");

    private final JwtDecoder jwtDecoder;
    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    private final SocialProfileRepository socialProfileRepository;
    private final GroupService groupService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        // MessageHeaderAccessor.getAccessor(...) — NOT StompHeaderAccessor.wrap(message) — is
        // required here: wrap() builds a brand-new accessor copy from the message's headers, so
        // accessor.setUser(...) on it never propagates back to the actual message. getAccessor()
        // retrieves the live, mutable accessor Spring's STOMP handling already attached to this
        // message (created with setLeaveMutable(true)), so mutating it actually sticks — otherwise
        // CONNECT appears to authenticate (no exception thrown) but the principal is silently lost,
        // and every later frame on the session (SEND/SUBSCRIBE) sees no user.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor));
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private AbstractAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        String token = StringUtils.hasText(header) && header.startsWith("Bearer ") ? header.substring(7) : null;

        if (token == null) {
            throw new MessagingException("Missing Authorization header on STOMP CONNECT");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new MessagingException("Invalid or expired JWT on STOMP CONNECT", e);
        }

        AbstractAuthenticationToken authentication;
        try {
            // Reuses the exact same JIT-provisioning/role-mapping path the REST filter chain
            // uses (KeycloakJwtAuthenticationConverter) — one code path, not a second copy of it
            // just because STOMP CONNECT doesn't go through the HTTP filter chain.
            authentication = keycloakJwtAuthenticationConverter.convert(jwt);
        } catch (AuthenticationException e) {
            throw new MessagingException("Rejected JWT on STOMP CONNECT: " + e.getMessage(), e);
        }

        log.debug("STOMP CONNECT authenticated for user: {}", jwt.getClaimAsString("email"));
        return authentication;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        Matcher matcher = CHANNEL_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        Integer channelId = Integer.valueOf(matcher.group(1));
        Integer userId = CurrentUserResolver.resolveUserId(accessor.getUser(), socialProfileRepository);
        if (!groupService.isChannelMember(userId, channelId)) {
            log.warn("Rejected subscription to channel {} — user {} is not a member", channelId, userId);
            throw new MessagingException("Not a member of this channel");
        }
    }
}
