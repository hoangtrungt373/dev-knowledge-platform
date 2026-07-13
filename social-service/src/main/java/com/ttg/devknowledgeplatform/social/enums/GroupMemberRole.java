package com.ttg.devknowledgeplatform.social.enums;

/**
 * A user's standing within a {@link com.ttg.devknowledgeplatform.social.entity.Group}, governing
 * which management actions they can perform (adding members, creating channels, promoting/demoting
 * other members). Exactly one member per group should hold {@code OWNER} at a time.
 */
public enum GroupMemberRole {
    OWNER,
    ADMIN,
    MEMBER
}
