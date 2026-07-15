package com.ttg.devknowledgeplatform.config.web;

import java.security.Principal;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.security.CurrentUserResolver;
import com.ttg.devknowledgeplatform.security.WebSocketConfig;

import lombok.RequiredArgsConstructor;

/**
 * Lets {@code @MessageMapping} methods accept {@code @CurrentUserId Integer userId}, the same
 * annotation REST controllers use — the STOMP-side counterpart to
 * {@link CurrentUserIdArgumentResolver} (Spring MVC's own, unrelated resolver interface of the
 * same name; this one implements Spring Messaging's {@link HandlerMethodArgumentResolver}). Shares
 * the actual cast-and-lookup logic with that REST resolver via {@link CurrentUserResolver} — the
 * two only differ in *how* they each find the {@code Principal} in the first place.
 *
 * <p>Registered via {@link WebSocketConfig#addArgumentResolvers}.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserIdMessageArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

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
        return CurrentUserResolver.resolveUserId(principal, userRepository);
    }
}
