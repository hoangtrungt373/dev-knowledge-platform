package com.ttg.devknowledgeplatform.identity.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String userUuid;
    private String username;
    private String email;
    private String role;
}
