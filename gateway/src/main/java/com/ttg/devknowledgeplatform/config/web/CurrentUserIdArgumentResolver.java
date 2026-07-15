package com.ttg.devknowledgeplatform.config.web;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUserId}-annotated controller parameters to the authenticated
 * user's integer primary key.
 *
 * <p>Reads {@link CustomOAuth2User} from the {@code SecurityContext} — the same principal
 * that {@code JwtAuthenticationFilter} stores after verifying the JWT. The principal's
 * {@code userUuid} field holds the user's public-facing UUID, not the internal numeric
 * primary key: neither the JWT claims nor the OAuth2 login flow ever expose the
 * sequential PK externally, so it can't be enumerated by a client. The actual cast +
 * {@link UserRepository#findByUserUuid} lookup lives in {@link CurrentUserResolver}, shared
 * with the STOMP-side {@code ws.CurrentUserIdMessageArgumentResolver} — this class's own job is
 * just finding the principal in the first place, which for REST means the
 * {@code SecurityContext}.
 *
 * <p>This is a <strong>HandlerMethodArgumentResolver</strong>: Spring MVC calls
 * {@link #supportsParameter} once per parameter (cached by the framework), and
 * {@link #resolveArgument} each time the method is invoked. The result arrives at the
 * controller as a plain {@code Integer}, removing the UUID-to-PK lookup boilerplate from
 * every endpoint method.
 *
 * <p>Registered in
 * {@link com.ttg.devknowledgeplatform.config.web.WebMvcConfig#addArgumentResolvers}.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    /**
     * Activates this resolver for any parameter annotated with {@code @CurrentUserId}
     * whose declared type is {@code Integer}.
     *
     * @param parameter the method parameter to check
     * @return {@code true} when both conditions are met
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Integer.class.equals(parameter.getParameterType());
    }

    /**
     * Extracts the user ID from the current {@code SecurityContext} principal.
     *
     * @param parameter     the annotated parameter
     * @param mavContainer  unused
     * @param webRequest    unused
     * @param binderFactory unused
     * @return the authenticated user's integer primary key
     * @throws IllegalStateException     if no authenticated principal is present (should
     *                                    never occur on routes already protected by {@code SecurityConfig})
     * @throws ResourceNotFoundException if the principal's UUID no longer matches a user row
     *                                    (e.g. the account was deleted after the JWT was issued)
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                   ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest,
                                   WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException(
                    "@CurrentUserId requires an authenticated principal, but the SecurityContext has none. "
                    + "Verify that the route is covered by SecurityConfig.");
        }
        return CurrentUserResolver.resolveUserId(auth, userRepository);
    }
}
