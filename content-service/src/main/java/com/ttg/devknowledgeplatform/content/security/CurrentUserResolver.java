package com.ttg.devknowledgeplatform.content.security;

import java.security.Principal;

import org.springframework.security.core.Authentication;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;

/**
 * Resolves the authenticated caller's Keycloak UUID from a {@link Principal}.
 *
 * <p>Reads the UUID straight off the {@link CustomOAuth2User} principal — no database lookup. This
 * module has no local {@code User} row to resolve against (see
 * {@link KeycloakJwtAuthenticationConverter}'s Javadoc for why): {@code ContentItem.authorUuid} is
 * compared/stamped directly against this value, never a foreign-key join.
 * {@code config.web.CurrentUserIdArgumentResolver} is this module's one caller.
 */
public final class CurrentUserResolver {

    private CurrentUserResolver() {
    }

    /**
     * @throws IllegalStateException if {@code principal} isn't an {@link Authentication} wrapping
     *                                a {@link CustomOAuth2User}
     */
    public static String resolveAuthorUuid(Principal principal) {
        if (!(principal instanceof Authentication auth) || !(auth.getPrincipal() instanceof CustomOAuth2User user)) {
            throw new IllegalStateException(
                    "@CurrentUserId requires an authenticated CustomOAuth2User principal, but none is present.");
        }
        return user.getUserUuid();
    }
}
