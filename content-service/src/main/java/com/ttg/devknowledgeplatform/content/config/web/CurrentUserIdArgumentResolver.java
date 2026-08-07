package com.ttg.devknowledgeplatform.content.config.web;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.content.security.CurrentUserResolver;
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
 * <p>Duplicated from {@code gateway}'s/{@code task-service}'s class of the same name —
 * {@code ArticleApi}/{@code QuestionAnswerApi} take {@code @CurrentUserId String authorUuid}
 * directly (not {@code @AuthenticationPrincipal CustomOAuth2User}), and {@code gateway}'s resolver
 * isn't reachable in-process anymore now that this module is a standalone app. Unlike
 * {@code gateway}'s resolver, this one never touches a database — see
 * {@link CurrentUserResolver}'s Javadoc for why this module has no local {@code User} row to look up.
 *
 * <p>Registered in {@link WebMvcConfig#addArgumentResolvers}.
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
        return CurrentUserResolver.resolveAuthorUuid(auth);
    }
}
