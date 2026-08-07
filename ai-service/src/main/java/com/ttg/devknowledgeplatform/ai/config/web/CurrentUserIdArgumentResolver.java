package com.ttg.devknowledgeplatform.ai.config.web;

import com.ttg.devknowledgeplatform.ai.security.CurrentUserResolver;
import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
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
 * <p>Duplicated from {@code gateway}'s/{@code task-service}'s/{@code content-service}'s class of
 * the same name — {@code ChatApi} takes {@code @CurrentUserId String userUuid} directly, and
 * {@code gateway}'s resolver (which resolves an {@code Integer} local PK, a different shape
 * entirely) will no longer be reachable in-process once this module is a standalone app. This one
 * never touches a database — see {@link CurrentUserResolver}'s Javadoc for why this module has no
 * local {@code User} row to look up.
 *
 * <p>Registered in {@link ChatMvcConfig#addArgumentResolvers}. Safe to add ahead of this module's
 * full standalone cutover — unlike a second {@code SecurityFilterChain}, argument resolvers are
 * additive (Spring tries each one via {@link #supportsParameter} until one matches), so this
 * coexists without conflict alongside {@code gateway}'s own {@code Integer}-typed resolver while
 * both are still in the same shared Spring context.
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
