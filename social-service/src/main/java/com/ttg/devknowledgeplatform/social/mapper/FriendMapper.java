package com.ttg.devknowledgeplatform.social.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.infra.service.StorageService;
import com.ttg.devknowledgeplatform.social.dto.friend.FriendRequestResponse;
import com.ttg.devknowledgeplatform.social.dto.friend.FriendSummaryResponse;
import com.ttg.devknowledgeplatform.social.dto.friend.UserSearchResultResponse;
import com.ttg.devknowledgeplatform.social.dto.friend.UserSummaryResponse;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.entity.Friendship;
import com.ttg.devknowledgeplatform.social.enums.RelationshipStatus;

/**
 * Maps friend-management entities from {@code social-service} to REST response records.
 *
 * <p>An abstract class rather than a plain interface — like {@code UserMapper} — because
 * presigned-URL resolution needs an injected {@link StorageService}, and MapStruct interfaces
 * can't hold instance fields.
 */
@Mapper(componentModel = "spring")
public abstract class FriendMapper {

    @Autowired
    protected StorageService storageService;

    @Mapping(target = "profilePicture", expression = "java(resolveProfilePicture(user))")
    public abstract UserSummaryResponse toUserSummary(User user);

    @Mapping(target = "status", expression = "java(friendRequest.getStatus().name())")
    @Mapping(target = "createdAt", source = "dteCreation")
    public abstract FriendRequestResponse toFriendRequestResponse(FriendRequest friendRequest);

    @Mapping(target = "user", expression = "java(toUserSummary(otherUser(friendship, viewerId)))")
    @Mapping(target = "friendsSince", source = "dteCreation")
    public abstract FriendSummaryResponse toFriendSummary(Friendship friendship, @Context Integer viewerId);

    public abstract UserSearchResultResponse toSearchResult(
            User user, RelationshipStatus relationshipStatus, long mutualFriendCount);

    protected String resolveProfilePicture(User user) {
        String pic = user.getProfilePicture();
        if (pic != null && !pic.startsWith("http")) {
            return storageService.getPresignedUrl(pic);
        }
        return pic;
    }

    protected User otherUser(Friendship friendship, Integer viewerId) {
        return friendship.getUser1().getId().equals(viewerId) ? friendship.getUser2() : friendship.getUser1();
    }
}
