package com.ttg.devknowledgeplatform.social.config.web;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.social.repository.SocialProfileRepository;
import com.ttg.devknowledgeplatform.social.security.CurrentUserResolver;
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
 * Resolves {@link CurrentUserId}-annotated controller parameters to the authenticated user's
 * integer primary key.
 *
 * <p>Duplicated from {@code gateway}'s class of the same name — {@code FriendApi}/{@code GroupApi}/
 * {@code DmApi} take {@code @CurrentUserId Integer userId} directly, and {@code gateway}'s resolver
 * isn't reachable in-process anymore now that this module is a standalone app. See
 * {@link CurrentUserResolver} for the actual UUID-to-PK lookup; this class's own job is just
 * finding the principal in the {@code SecurityContext}.
 *
 * <p>Registered in {@link WebMvcConfig#addArgumentResolvers}.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final SocialProfileRepository socialProfileRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Integer.class.equals(parameter.getParameterType());
    }

    /**
     * @throws IllegalStateException     if no authenticated principal is present (should never
     *                                    occur on routes already protected by {@code SecurityConfig})
     * @throws ResourceNotFoundException if the principal's UUID no longer matches a profile row
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
        return CurrentUserResolver.resolveUserId(auth, socialProfileRepository);
    }
}
