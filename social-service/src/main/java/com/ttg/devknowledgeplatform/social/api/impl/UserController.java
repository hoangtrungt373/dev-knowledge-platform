package com.ttg.devknowledgeplatform.social.api.impl;

import java.util.Set;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.social.api.UserApi;
import com.ttg.devknowledgeplatform.social.dto.friend.UserInfoResponse;
import com.ttg.devknowledgeplatform.social.dto.friend.UserSearchResultResponse;
import com.ttg.devknowledgeplatform.social.entity.SocialProfile;
import com.ttg.devknowledgeplatform.social.enums.RelationshipStatus;
import com.ttg.devknowledgeplatform.social.mapper.FriendMapper;
import com.ttg.devknowledgeplatform.social.repository.SocialProfileRepository;
import com.ttg.devknowledgeplatform.social.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link UserApi}.
 *
 * <p>Bean explicitly named {@code socialUserController} — {@code identity-service} has its own,
 * unrelated {@code UserController} (different {@code UserApi}, different package) fronting pure
 * profile-mutation endpoints. Spring's default bean name is the decapitalized simple class name
 * only, ignoring package, so both would otherwise collide if ever assembled into one context.
 *
 * <p>Resolves the base lookup directly via this module's own {@link SocialProfileRepository} —
 * never {@code identity-service}'s {@code UserService}, which is a standalone service now and
 * can't be reached in-process. This module's own {@code social.PROFILE} row is kept in sync
 * independently, by this module's own JIT-provisioning logic (see
 * {@code security.KeycloakJwtAuthenticationConverter}).
 */
@RestController("socialUserController")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "username", "dteCreation");

    private final SocialProfileRepository socialProfileRepository;
    private final FriendService friendService;
    private final FriendMapper friendMapper;

    @Override
    public ResponseEntity<UserInfoResponse> getPublicProfile(CustomOAuth2User principal, String userUuid) {
        SocialProfile user = Validator.notFound(
                socialProfileRepository.findByProfileUuid(userUuid), CommonErrorCode.USER_NOT_FOUND, "User not found: " + userUuid);
        UserInfoResponse response = friendMapper.toUserInfo(user);

        if (principal != null) {
            Integer viewerId = resolveCurrentUserId(principal);
            if (!viewerId.equals(user.getId())) {
                // Throws 404 (not a distinguishable "blocked" error) if the target has blocked
                // the viewer, preserving mutual invisibility.
                RelationshipStatus relationship = friendService.getRelationshipStatus(viewerId, userUuid);
                response.setRelationshipStatus(relationship.name());
                response.setMutualFriendCount(friendService.countMutualFriends(viewerId, userUuid));
            }
        }

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<UserSearchResultResponse>> search(
            CustomOAuth2User principal, String q, int page, int size, String sortBy, String sortDir) {
        Integer viewerId = resolveCurrentUserId(principal);
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.searchUsers(viewerId, q, pageable)
                .map(user -> friendMapper.toSearchResult(
                        user,
                        friendService.getRelationshipStatus(viewerId, user.getProfileUuid()),
                        friendService.countMutualFriends(viewerId, user.getProfileUuid())));
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private Integer resolveCurrentUserId(CustomOAuth2User principal) {
        return Validator.notFound(socialProfileRepository.findByEmail(principal.getEmail()), CommonErrorCode.USER_NOT_FOUND)
                .getId();
    }
}
