package com.ttg.devknowledgeplatform.social.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code social-service} — friend requests, friendships, and blocking.
 *
 * Format: MODULE_ACTION_ERROR
 * Example: CANNOT_FRIEND_SELF, ALREADY_FRIENDS
 */
@Getter
public enum FriendErrorCode implements ErrorCode {

    // Friend Management Errors (FRIEND_*)
    CANNOT_FRIEND_SELF("FRIEND_001", "You cannot send a friend request to yourself", HttpStatus.BAD_REQUEST),
    FRIEND_REQUEST_ALREADY_EXISTS("FRIEND_002", "A pending friend request already exists between these users", HttpStatus.CONFLICT),
    FRIEND_REQUEST_NOT_FOUND("FRIEND_003", "Friend request not found", HttpStatus.NOT_FOUND),
    ALREADY_FRIENDS("FRIEND_004", "These users are already friends", HttpStatus.CONFLICT),
    NOT_FRIENDS("FRIEND_005", "These users are not friends", HttpStatus.BAD_REQUEST),
    USER_ALREADY_BLOCKED("FRIEND_006", "This user is already blocked", HttpStatus.CONFLICT),
    CANNOT_BLOCK_SELF("FRIEND_007", "You cannot block yourself", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    FriendErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
