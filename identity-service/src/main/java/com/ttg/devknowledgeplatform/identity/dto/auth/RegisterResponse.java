package com.ttg.devknowledgeplatform.identity.dto.auth;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class RegisterResponse {
    private final String email;
    private final String message;
}
