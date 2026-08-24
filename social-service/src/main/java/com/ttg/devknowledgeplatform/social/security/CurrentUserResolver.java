package com.ttg.devknowledgeplatform.social.security;

import java.security.Principal;

import org.springframework.security.core.Authentication;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.social.repository.SocialProfileRepository;

/**
 * Resolves the authenticated user's integer primary key from a {@link Principal}.
 *
 * <p>Duplicated from {@code gateway}'s class of the same name — this module has no Maven
 * dependency on {@code gateway} and needs its own copy now that {@code @CurrentUserId} is resolved
 * against this app's own {@link com.ttg.devknowledgeplatform.social.entity.SocialProfile} table
 * rather than {@code gateway}'s {@code product.USER}. Both
 * {@code config.web.CurrentUserIdArgumentResolver} (REST) and
 * {@code ws.CurrentUserIdMessageArgumentResolver} (STOMP) call this — the one shared tail between
 * the two transports, same shape as {@code gateway}'s own split before this module was extracted.
 */
public final class CurrentUserResolver {

    private CurrentUserResolver() {
    }

    /**
     * @throws IllegalStateException     if {@code principal} isn't an {@link Authentication}
     *                                    wrapping a {@link CustomOAuth2User}
     * @throws ResourceNotFoundException if the principal's UUID no longer matches a profile row
     */
    public static Integer resolveUserId(Principal principal, SocialProfileRepository socialProfileRepository) {
        if (!(principal instanceof Authentication auth) || !(auth.getPrincipal() instanceof CustomOAuth2User user)) {
            throw new IllegalStateException(
                    "@CurrentUserId requires an authenticated CustomOAuth2User principal, but none is present.");
        }
        return Validator.notFound(
                        socialProfileRepository.findByProfileUuid(user.getUserUuid()),
                        CommonErrorCode.USER_NOT_FOUND, "No profile found for UUID: " + user.getUserUuid())
                .getId();
    }
}
