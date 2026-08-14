package com.ttg.devknowledgeplatform.ai.config.web;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.infra.security.CurrentUserResolver;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUserId}-annotated controller parameters to the authenticated caller's
 * Keycloak UUID.
 *
 * <p>{@code ChatApi} takes {@code @CurrentUserId String userUuid} directly. {@link CurrentUserResolver}
 * is now a shared {@code infra} class (see its own Javadoc) rather than a local copy — this
 * module's own copy happened to already use the same {@code resolveUserUuid} method name the
 * shared class settled on, so this consolidation needed no call-site rename here, unlike
 * {@code task-service}'s/{@code content-service}'s own resolvers.
 *
 * <p>Registered in {@link ChatMvcConfig#addArgumentResolvers}.
 */
@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && String.class.equals(parameter.getParameterType());
    }

    /**
     * @throws IllegalStateException if no authenticated principal is present (should never occur
     *                                on routes already protected by {@code SecurityConfig})
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
        return CurrentUserResolver.resolveUserUuid(auth);
    }
}
