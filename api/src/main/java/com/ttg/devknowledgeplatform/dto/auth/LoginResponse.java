package com.ttg.devknowledgeplatform.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String username;
    private String email;
    private String role;
}
