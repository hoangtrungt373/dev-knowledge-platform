package com.ttg.devknowledgeplatform.identity.security.service.impl;

import java.util.List;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.identity.config.KeycloakAdminProperties;
import com.ttg.devknowledgeplatform.identity.dto.auth.RegisterRequest;
import com.ttg.devknowledgeplatform.identity.exception.IdentityErrorCode;
import com.ttg.devknowledgeplatform.identity.security.service.KeycloakAdminService;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link KeycloakAdminService}, backed by Keycloak's Admin REST API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminServiceImpl implements KeycloakAdminService {

    private final Keycloak keycloakAdminClient;
    private final KeycloakAdminProperties properties;

    @Override
    public void createUser(RegisterRequest request) {
        UserRepresentation user = new UserRepresentation();
        // registrationEmailAsUsername: true on the realm — username and email are always the same.
        user.setUsername(request.email());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        // Created pre-verified rather than requiring Keycloak's "Verify Email" required action —
        // a deliberate scope choice (see docs/CHANGELOG.md), not an oversight.
        user.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        try (Response response = keycloakAdminClient.realm(properties.getRealm()).users().create(user)) {
            if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                return;
            }
            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new BusinessException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, new Object[]{request.email()});
            }
            log.error("Keycloak user creation failed for {}: HTTP {}", request.email(), response.getStatus());
            throw new BusinessException(IdentityErrorCode.KEYCLOAK_USER_CREATE_FAILED);
        }
    }
}
