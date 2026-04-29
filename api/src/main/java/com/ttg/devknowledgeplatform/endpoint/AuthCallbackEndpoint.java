package com.ttg.devknowledgeplatform.endpoint;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.service.StateTokenService;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for exchanging state token for actual JWT tokens
 * 
 * Frontend calls this endpoint after receiving state token from OAuth2 redirect
 * 
 * Endpoint: POST /api/v1/auth/exchange-state
 * Body: { "state": "uuid-state-token" }
 * Response: { "accessToken": "...", "refreshToken": "...", "userId": "...", ... }
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthCallbackEndpoint {
    
    private final StateTokenService stateTokenService;
    
    @PostMapping("/exchange-state")
    public ResponseEntity<AuthTokensResponse> exchangeStateToken(@RequestBody Map<String, String> request) {
        String stateToken = request.get("state");
        
        if (stateToken == null || stateToken.isEmpty()) {
            throw new ApiException(ErrorCode.OAUTH_STATE_TOKEN_MISSING);
        }
        
        // Retrieve tokens from cache/storage
        Map<String, String> tokenData = stateTokenService.getTokenData(stateToken);
        
        if (tokenData == null) {
            log.warn("Invalid or expired state token: {}", stateToken);
            throw new ApiException(ErrorCode.OAUTH_STATE_TOKEN_INVALID);
        }
        
        // Delete state token (one-time use)
        stateTokenService.deleteTokenData(stateToken);
        
        log.info("Exchanged state token for user: {}", tokenData.get("userId"));
        
        // Return tokens to frontend
        AuthTokensResponse response = AuthTokensResponse.builder()
            .accessToken(tokenData.get("accessToken"))
            .refreshToken(tokenData.get("refreshToken"))
            .userId(tokenData.get("userId"))
            .username(tokenData.get("username"))
            .email(tokenData.get("email"))
            .role(tokenData.get("role"))
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Data
    @Builder
    public static class AuthTokensResponse {
        private String accessToken;
        private String refreshToken;
        private String userId;
        private String username;
        private String email;
        private String role;
    }
}

