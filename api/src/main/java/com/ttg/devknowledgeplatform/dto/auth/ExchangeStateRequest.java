package com.ttg.devknowledgeplatform.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExchangeStateRequest {
    @NotBlank(message = "State token is required")
    private String stateToken;
}
