package com.ttg.devknowledgeplatform.identity.security.service.impl;

import java.util.List;

import org.keycloak.admin.client.CreatedResponseUtil;
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
        // Created unverified — sendVerifyEmail below emails a real Keycloak "Verify Email"
        // action-token link, replacing the previous "created pre-verified" scope choice.
        user.setEmailVerified(false);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        String keycloakUserId;
        try (Response response = keycloakAdminClient.realm(properties.getRealm()).users().create(user)) {
            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new BusinessException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, new Object[]{request.email()});
            }
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                log.error("Keycloak user creation failed for {}: HTTP {}", request.email(), response.getStatus());
                throw new BusinessException(IdentityErrorCode.KEYCLOAK_USER_CREATE_FAILED);
            }
            keycloakUserId = CreatedResponseUtil.getCreatedId(response);
        }

        // Best-effort — a mail-server hiccup shouldn't fail registration itself (the account
        // exists either way); the user can always trigger resendVerificationEmail later.
        try {
            sendVerifyEmail(keycloakUserId);
        } catch (Exception e) {
            log.warn("Account {} created but failed to send its verification email: {}", request.email(), e.getMessage());
        }
    }

    @Override
    public void resendVerificationEmail(String keycloakSubjectId) {
        try {
            sendVerifyEmail(keycloakSubjectId);
        } catch (Exception e) {
            log.error("Failed to resend verification email for Keycloak subject {}: {}", keycloakSubjectId, e.getMessage());
            throw new BusinessException(IdentityErrorCode.VERIFICATION_EMAIL_SEND_FAILED);
        }
    }

    private void sendVerifyEmail(String keycloakUserId) {
        // void, not Response — the generated client proxy throws (e.g. WebApplicationException)
        // on a non-2xx reply itself, so there's no status code here to check by hand.
        // client_id/redirect_uri land the user back in this app instead of Keycloak's default
        // target — its own generic "account" client confirmation page, a dead end from this app's
        // perspective. /login rather than /dashboard deliberately: if the user already logged out
        // since registering, GuestRoute would otherwise bounce them /dashboard -> /login anyway (an
        // extra pointless hop to the same place); if they're still logged in, GuestRoute's own
        // already-authenticated redirect sends them on to /dashboard regardless — one target
        // handles both cases via routing logic gui already has. emailVerified=true lets whichever
        // page actually renders (Login.tsx or, via that redirect, Dashboard.tsx) show a one-time
        // confirmation toast instead of silently landing with no feedback either way.
        keycloakAdminClient.realm(properties.getRealm())
                .users().get(keycloakUserId)
                .sendVerifyEmail("gui", properties.getFrontendUrl() + "/login?emailVerified=true");
    }
}
