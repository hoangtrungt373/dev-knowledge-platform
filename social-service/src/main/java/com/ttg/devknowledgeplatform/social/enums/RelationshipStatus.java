package com.ttg.devknowledgeplatform.social.enums;

/**
 * Computed relationship between two users, from the viewpoint of the viewing user.
 *
 * <p>Not persisted — derived on demand from {@code FRIENDSHIP}, {@code FRIEND_REQUEST}, and
 * {@code USER_BLOCK} rows to decide which action (add/cancel/accept/unfriend) a profile or
 * search result should offer.
 */
public enum RelationshipStatus {
    STRANGER,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIENDS,
    BLOCKED
}
