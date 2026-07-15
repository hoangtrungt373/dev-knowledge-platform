package com.ttg.devknowledgeplatform.security;

import java.security.Principal;

import org.springframework.security.core.Authentication;

import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;

/**
 * Resolves the authenticated user's integer primary key from a {@link Principal} — the one piece
 * that's genuinely identical between {@code @CurrentUserId}'s two resolvers:
 * {@code config.web.CurrentUserIdArgumentResolver} (Spring MVC, reads the principal from
 * {@code SecurityContextHolder}) and {@code ws.CurrentUserIdMessageArgumentResolver} (Spring
 * Messaging, reads it from the STOMP session's headers). Both transports find the principal a
 * different way — REST via a request-scoped {@code ThreadLocal}, STOMP via a value carried on the
 * message itself, since STOMP handling never runs on the thread that established the original HTTP
 * request — but once found, both hold the same {@link CustomOAuth2User} shape, which is what this
 * shared tail actually operates on.
 */
public final class CurrentUserResolver {

    private CurrentUserResolver() {
    }

    /**
     * @throws IllegalStateException     if {@code principal} isn't an {@link Authentication}
     *                                    wrapping a {@link CustomOAuth2User}
     * @throws ResourceNotFoundException if the principal's UUID no longer matches a user row
     */
    public static Integer resolveUserId(Principal principal, UserRepository userRepository) {
        if (!(principal instanceof Authentication auth) || !(auth.getPrincipal() instanceof CustomOAuth2User user)) {
            throw new IllegalStateException(
                    "@CurrentUserId requires an authenticated CustomOAuth2User principal, but none is present.");
        }
        return userRepository.findByUserUuid(user.getUserUuid())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.USER_NOT_FOUND, "No user found for UUID: " + user.getUserUuid()))
                .getId();
    }
}
