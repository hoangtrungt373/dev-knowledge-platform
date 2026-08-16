package com.ttg.devknowledgeplatform.identity.security.service;

import com.ttg.devknowledgeplatform.identity.dto.auth.RegisterRequest;

/**
 * Creates Keycloak user accounts via the Admin REST API — the only way to create a user server-side,
 * since Keycloak's token endpoint can only authenticate an existing one.
 */
public interface KeycloakAdminService {

    /**
     * Creates a new, already-enabled and email-verified Keycloak user from {@code request}.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if the email is
     *         already registered ({@code IdentityErrorCode.EMAIL_ALREADY_EXISTS}) or Keycloak
     *         otherwise rejects the request ({@code IdentityErrorCode.KEYCLOAK_USER_CREATE_FAILED})
     */
    void createUser(RegisterRequest request);
}
