package com.ttg.devknowledgeplatform.identity.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExchangeStateRequest {
    @NotBlank(message = "State token is required")
    private String stateToken;
}
