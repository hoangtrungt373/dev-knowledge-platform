package com.ttg.devknowledgeplatform.task.security;

import java.security.Principal;

import org.springframework.security.core.Authentication;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;

/**
 * Resolves the authenticated caller's Keycloak UUID from a {@link Principal}.
 *
 * <p>Reads the UUID straight off the {@link CustomOAuth2User} principal — no database lookup. This
 * module has no local {@code User} row to resolve against (see
 * {@link KeycloakJwtAuthenticationConverter}'s Javadoc for why): {@code Project}/{@code Task}
 * ownership is a plain {@code ownerUuid} column compared directly against this value, never a
 * foreign-key join. {@code config.web.CurrentUserIdArgumentResolver} is this module's one caller
 * (no STOMP transport here, unlike {@code gateway}, so there's no second resolver to share this
 * tail with).
 */
public final class CurrentUserResolver {

    private CurrentUserResolver() {
    }

    /**
     * @throws IllegalStateException if {@code principal} isn't an {@link Authentication} wrapping
     *                                a {@link CustomOAuth2User}
     */
    public static String resolveOwnerUuid(Principal principal) {
        if (!(principal instanceof Authentication auth) || !(auth.getPrincipal() instanceof CustomOAuth2User user)) {
            throw new IllegalStateException(
                    "@CurrentUserId requires an authenticated CustomOAuth2User principal, but none is present.");
        }
        return user.getUserUuid();
    }
}
