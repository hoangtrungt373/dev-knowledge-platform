package com.ttg.devknowledgeplatform.dto;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoResponse {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profilePicture;
    private String provider;
    private String role;
    private Boolean emailVerified;
    private String status;
    private Instant createdAt;
    private Instant lastModified;

    /** Viewer's relationship to this user (e.g. {@code FRIENDS}, {@code STRANGER}); {@code null} when viewing anonymously or viewing your own profile. */
    private String relationshipStatus;

    /** Friends in common with the viewer; {@code null} under the same conditions as {@link #relationshipStatus}. */
    private Long mutualFriendCount;
}
