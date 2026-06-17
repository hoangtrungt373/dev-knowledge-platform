package com.ttg.devknowledgeplatform.dto.auth;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class RegisterResponse {
    private final String email;
    private final String message;
}
