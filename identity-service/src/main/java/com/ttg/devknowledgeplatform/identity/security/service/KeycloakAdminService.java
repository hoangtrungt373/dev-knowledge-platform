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

    /**
     * Renames a Keycloak user's {@code username} via the Admin REST API.
     *
     * <p>Keycloak, not the local {@code User} row, is this reactor's source of truth for identity
     * claims — {@code preferred_username} is re-synced from Keycloak into the local row on every
     * authenticated request (see {@code UserService#findOrCreateFromKeycloak}), so a local-only
     * rename would be silently reverted on the caller's very next request. This must be called
     * before the local row is updated, so a Keycloak-side conflict leaves nothing to roll back.
     *
     * @param keycloakSubjectId the account's Keycloak subject id ({@code sub} claim /
     *                          {@code User.keycloakSubjectId}), not the local numeric PK
     * @param newUsername       the new username, already validated/normalized by the caller
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if the username is
     *         already taken in the realm ({@code CommonErrorCode.USER_USERNAME_ALREADY_EXISTS}) or
     *         Keycloak otherwise rejects the request ({@code IdentityErrorCode.KEYCLOAK_USER_UPDATE_FAILED})
     */
    void updateUsername(String keycloakSubjectId, String newUsername);
}
