package com.ttg.devknowledgeplatform.social.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code social-service} — friend requests/friendships/blocking, groups and
 * channels, and 1:1 DMs. One enum per module (not per sub-domain), same as {@code content-service}'s
 * {@code ContentErrorCode} holding {@code CATEGORY_*}/{@code TAG_*}/{@code QA_*}/{@code ARTICLE_*}
 * together — renamed from {@code FriendErrorCode} once this module grew beyond just the friend
 * graph.
 *
 * Format: MODULE_ACTION_ERROR
 * Example: CANNOT_FRIEND_SELF, ALREADY_FRIENDS, INSUFFICIENT_GROUP_ROLE
 */
@Getter
public enum SocialErrorCode implements ErrorCode {

    // Friend Management Errors (FRIEND_*)
    CANNOT_FRIEND_SELF("FRIEND_001", "You cannot send a friend request to yourself", HttpStatus.BAD_REQUEST),
    FRIEND_REQUEST_ALREADY_EXISTS("FRIEND_002", "A pending friend request already exists between these users", HttpStatus.CONFLICT),
    FRIEND_REQUEST_NOT_FOUND("FRIEND_003", "Friend request not found", HttpStatus.NOT_FOUND),
    ALREADY_FRIENDS("FRIEND_004", "These users are already friends", HttpStatus.CONFLICT),
    NOT_FRIENDS("FRIEND_005", "These users are not friends", HttpStatus.BAD_REQUEST),
    USER_ALREADY_BLOCKED("FRIEND_006", "This user is already blocked", HttpStatus.CONFLICT),
    CANNOT_BLOCK_SELF("FRIEND_007", "You cannot block yourself", HttpStatus.BAD_REQUEST),

    // Direct Message Errors (DM_*)
    DM_FRIEND_REQUIRED("DM_001", "You can only message users who are your friends", HttpStatus.BAD_REQUEST),
    DM_THREAD_NOT_FOUND("DM_002", "DM conversation not found", HttpStatus.NOT_FOUND),

    // Group Errors (GROUP_*)
    GROUP_NOT_FOUND("GROUP_001", "Group not found", HttpStatus.NOT_FOUND),
    NOT_GROUP_MEMBER("GROUP_002", "You are not a member of this group", HttpStatus.FORBIDDEN),
    INSUFFICIENT_GROUP_ROLE("GROUP_003", "You do not have permission to perform this action in this group", HttpStatus.FORBIDDEN),
    GROUP_MEMBER_NOT_FOUND("GROUP_004", "This user is not a member of this group", HttpStatus.NOT_FOUND),
    CANNOT_REMOVE_OWNER("GROUP_005", "The group owner cannot be removed", HttpStatus.FORBIDDEN),
    CANNOT_CHANGE_OWNER_ROLE("GROUP_006", "Ownership cannot be reassigned yet", HttpStatus.BAD_REQUEST),
    OWNER_CANNOT_LEAVE_GROUP("GROUP_007", "The group owner cannot leave the group", HttpStatus.FORBIDDEN),

    // Channel Errors (CHANNEL_*)
    CHANNEL_NOT_FOUND("CHANNEL_001", "Channel not found", HttpStatus.NOT_FOUND),
    CHANNEL_NAME_ALREADY_EXISTS("CHANNEL_002", "A channel with this name already exists in this group", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    SocialErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
