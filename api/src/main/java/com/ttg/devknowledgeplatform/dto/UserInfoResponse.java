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
}
