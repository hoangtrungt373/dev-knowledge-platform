package com.ttg.devknowledgeplatform.service;

import java.util.Map;
import java.util.UUID;

public interface StateTokenService {

    Map<String, String> storeTokenData(String stateToken, Map<String, String> tokenData, long expirationSeconds);

    Map<String, String> getTokenData(String stateToken);

    void deleteTokenData(String stateToken);

    default String generateStateToken() {
        return UUID.randomUUID().toString();
    }
}

