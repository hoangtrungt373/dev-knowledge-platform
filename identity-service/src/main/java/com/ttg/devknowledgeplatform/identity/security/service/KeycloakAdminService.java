package com.ttg.devknowledgeplatform.identity.security.service;

import com.ttg.devknowledgeplatform.identity.dto.auth.RegisterRequest;

/**
 * Creates Keycloak user accounts via the Admin REST API — the only way to create a user server-side,
 * since Keycloak's token endpoint can only authenticate an existing one.
 */
public interface KeycloakAdminService {

    /**
     * Creates a new, already-enabled Keycloak user from {@code request}, created with
     * {@code emailVerified: false} — a real verification email (Keycloak's own "Verify Email"
     * action-token link) is sent as a best-effort follow-up, never failing registration itself if
     * the send fails (the account still exists either way; {@link #resendVerificationEmail} covers
     * that case).
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if the email is
     *         already registered ({@code IdentityErrorCode.EMAIL_ALREADY_EXISTS}) or Keycloak
     *         otherwise rejects the request ({@code IdentityErrorCode.KEYCLOAK_USER_CREATE_FAILED})
     */
    void createUser(RegisterRequest request);

    /**
     * Re-sends Keycloak's own email-verification link to an existing account.
     *
     * @param keycloakSubjectId the account's Keycloak subject id ({@code sub} claim /
     *                          {@code User.keycloakSubjectId}), not the local numeric PK
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if Keycloak rejects
     *         the request ({@code IdentityErrorCode.VERIFICATION_EMAIL_SEND_FAILED})
     */
    void resendVerificationEmail(String keycloakSubjectId);
}
