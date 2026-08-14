package com.ttg.devknowledgeplatform.infra.security;

import java.security.Principal;

import org.springframework.security.core.Authentication;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;

/**
 * Resolves the authenticated caller's Keycloak UUID from a {@link Principal}.
 *
 * <p>Reads the UUID straight off the {@link CustomOAuth2User} principal — no database lookup.
 * Shared here rather than duplicated per service (as it used to be, in {@code task-service}'s,
 * {@code content-service}'s, and {@code ai-service}'s own {@code security} packages under three
 * different method names — {@code resolveOwnerUuid}/{@code resolveAuthorUuid}/
 * {@code resolveUserUuid} — despite identical bodies) — every service with a plain
 * {@code ownerUuid}/{@code authorUuid}/{@code userUuid} column compares it against this same value,
 * regardless of what that column is called locally. Picked up automatically by each of those
 * services' existing {@code @ComponentScan(basePackages = {"...own...",
 * "com.ttg.devknowledgeplatform.infra"})} — the caller resolves the value once and assigns it to
 * whatever locally-named variable it needs.
 *
 * <p><strong>Not used by {@code social-service}</strong> (resolves a real local
 * {@code SocialProfile} numeric PK via a repository lookup, not a claims-only read),
 * {@code ecommerce-service} (never uses {@code @CurrentUserId} at all — no entity with an
 * owner/author column), or {@code identity-service} (resolves the caller via
 * {@code @AuthenticationPrincipal} directly in the controller instead of this helper-class
 * pattern). {@code gateway} has no consumer of this class left either (zero REST controllers).
 */
public final class CurrentUserResolver {

    private CurrentUserResolver() {
    }

    /**
     * @throws IllegalStateException if {@code principal} isn't an {@link Authentication} wrapping
     *                                a {@link CustomOAuth2User}
     */
    public static String resolveUserUuid(Principal principal) {
        if (!(principal instanceof Authentication auth) || !(auth.getPrincipal() instanceof CustomOAuth2User user)) {
            throw new IllegalStateException(
                    "@CurrentUserId requires an authenticated CustomOAuth2User principal, but none is present.");
        }
        return user.getUserUuid();
    }
}
