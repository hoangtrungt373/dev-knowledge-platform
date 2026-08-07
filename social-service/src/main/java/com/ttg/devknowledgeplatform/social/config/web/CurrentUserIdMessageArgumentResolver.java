package com.ttg.devknowledgeplatform.social.config.web;

import java.security.Principal;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.social.repository.SocialProfileRepository;
import com.ttg.devknowledgeplatform.social.security.CurrentUserResolver;
import com.ttg.devknowledgeplatform.social.security.WebSocketConfig;

import lombok.RequiredArgsConstructor;

/**
 * Lets {@code @MessageMapping} methods accept {@code @CurrentUserId Integer userId}, the same
 * annotation REST controllers use — the STOMP-side counterpart to
 * {@link CurrentUserIdArgumentResolver} (Spring MVC's own, unrelated resolver interface of the
 * same name; this one implements Spring Messaging's {@link HandlerMethodArgumentResolver}). Shares
 * the actual cast-and-lookup logic with that REST resolver via {@link CurrentUserResolver} — the
 * two only differ in *how* they each find the {@code Principal} in the first place.
 *
 * <p>Duplicated from {@code gateway}'s class of the same name — relocated here alongside
 * {@code GroupMessagingController}/{@code DmMessagingController}, since {@code gateway} no longer
 * has a Maven dependency on this module (and this module now owns the whole STOMP transport for
 * chat, {@code gateway} never had a second use for it).
 *
 * <p>Registered via {@link WebSocketConfig#addArgumentResolvers}.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserIdMessageArgumentResolver implements HandlerMethodArgumentResolver {

    private final SocialProfileRepository socialProfileRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Integer.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, Message<?> message) {
        Principal principal = SimpMessageHeaderAccessor.wrap(message).getUser();
        if (principal == null) {
            throw new IllegalStateException(
                    "@CurrentUserId requires an authenticated STOMP session, but none is present. "
                    + "Verify StompAuthChannelInterceptor ran on CONNECT.");
        }
        return CurrentUserResolver.resolveUserId(principal, socialProfileRepository);
    }
}
