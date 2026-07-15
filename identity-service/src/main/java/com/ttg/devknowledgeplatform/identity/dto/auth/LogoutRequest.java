package com.ttg.devknowledgeplatform.identity.dto.auth;

import lombok.Data;

@Data
public class LogoutRequest {
    private String refreshToken;
}
