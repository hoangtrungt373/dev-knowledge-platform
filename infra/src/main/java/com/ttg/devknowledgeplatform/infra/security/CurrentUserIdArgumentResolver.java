package com.ttg.devknowledgeplatform.infra.security;

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
 * Keycloak UUID, via {@link CurrentUserResolver#resolveUserUuid}.
 *
 * <p>Moved here from four near-identical per-service copies (`content-service`, `task-service`,
 * `ai-service`, `ecommerce-service`) — each carried byte-for-byte identical logic, differing only
 * in Javadoc wording (each module's own `ownerUuid`/`authorUuid`/`userUuid` column-naming
 * vocabulary). Consolidating this class is the same move this module already made for
 * {@link CurrentUserResolver} itself (the static claims-only resolution logic this class wraps in
 * Spring MVC's argument-resolver plumbing) and for {@link KeycloakRealmRoleConverter}/
 * {@link JsonAuthenticationEntryPoint} — see this class's Javadoc for the general shape of what
 * gets shared here vs. kept local.
 *
 * <p><strong>Not used by</strong> `social-service` (resolves a real local `SocialProfile` numeric
 * PK via a repository lookup — genuinely divergent logic, not duplication, so it keeps its own
 * local `Integer`-typed copy plus a STOMP-side `CurrentUserIdMessageArgumentResolver` counterpart
 * this class has no equivalent for) or `identity-service` (resolves the caller via
 * `@AuthenticationPrincipal` directly in the controller instead of this helper-class pattern, so
 * it never had a copy of this class to begin with). `gateway` has no consumer either (zero REST
 * controllers).
 *
 * <p>Each of the four consuming modules' own `@SpringBootApplication` needs an explicit
 * {@code @Import(CurrentUserIdArgumentResolver.class)} (this is a {@code @Component}, not a bare
 * properties class, but still needs the explicit import per this reactor's "explicit imports over
 * broad component scan" convention — see this module's own top-level Javadoc note); each module's
 * own `WebMvcConfig`/`ChatMvcConfig` still registers it locally via
 * {@code addArgumentResolvers} — only the resolver class itself moved, not the registration step.
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
