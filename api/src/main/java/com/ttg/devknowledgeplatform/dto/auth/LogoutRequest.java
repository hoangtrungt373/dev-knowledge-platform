package com.ttg.devknowledgeplatform.dto.auth;

import lombok.Data;

@Data
public class LogoutRequest {
    private String refreshToken;
}
