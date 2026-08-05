package com.ttg.devknowledgeplatform.identity.security.service;

import java.util.Optional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;

/**
 * Manages the lifecycle of {@link User} accounts.
 *
 * <p>Lookup methods come in two flavours: throwing variants (no {@code Optional}) that
 * raise a {@code ResourceNotFoundException} when the user is absent, and {@code Optional}
 * variants for call sites where absence is a handled case rather than an error.
 *
 * <p>Login/registration/password/OTP-email are no longer this module's concern — Keycloak owns
 * that whole lifecycle now (see {@code docs/CHANGELOG.md}'s Keycloak migration entry). This
 * service's role narrowed to: resolving "the acting user" for a request, editable-profile updates,
 * and {@link #findOrCreateFromKeycloak}, which keeps the local row in sync with Keycloak.
 */
public interface UserService {

    /**
     * Resolves the local {@link User} row for a verified Keycloak identity, creating it on first
     * sight (JIT provisioning) or refreshing its denormalized fields if they've drifted from the
     * token's claims.
     *
     * <p>Called on every authenticated request (it has to resolve the local numeric PK for
     * {@code @CurrentUserId} and every {@code User} foreign key regardless), so implementations
     * should only write when something actually changed, not unconditionally on every call.
     *
     * @param info the claims extracted from a verified Keycloak access token
     * @return the linked (or newly created) {@link User}
     */
    User findOrCreateFromKeycloak(KeycloakUserInfo info);

    /**
     * Returns the user with the given email address, throwing if not found.
     *
     * @param email the email to look up
     * @return the matching {@link User}
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no user exists with that email
     */
    User findByEmail(String email);

    /**
     * Resolves the {@link User} entity behind an authenticated OAuth2 principal.
     *
     * <p>Centralizes the {@code principal.getEmail()} → {@code findByEmail} lookup that would
     * otherwise be repeated in every controller needing "the acting user."
     *
     * @param principal the authenticated principal from the security context
     * @return the matching {@link User}
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no user exists with that email
     */
    User resolveCurrentUser(CustomOAuth2User principal);

    /**
     * Returns the user with the given public UUID, throwing if not found.
     *
     * @param userUuid the public-facing UUID (not the surrogate primary key)
     * @return the matching {@link User}
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no user exists with that UUID
     */
    User findByUserUuid(String userUuid);

    /**
     * Returns the user with the given surrogate primary key, or empty if not found.
     *
     * @param userId the database primary key
     * @return an {@link Optional} containing the user, or empty
     */
    Optional<User> findById(Integer userId);

    /**
     * Returns the user with the given public UUID, or empty if not found.
     *
     * <p>Prefer {@link #findByUserUuid(String)} when absence should be treated as an error.
     *
     * @param userUuid the public-facing UUID
     * @return an {@link Optional} containing the user, or empty
     */
    Optional<User> findByUserUuidOptional(String userUuid);

    /**
     * Updates the online/offline presence status of a user.
     *
     * @param userId the surrogate primary key of the user
     * @param status the new {@link UserStatus}
     */
    void updateStatus(Integer userId, UserStatus status);

    /**
     * Updates the editable profile fields of the authenticated user.
     *
     * @param email     identifies the user to update
     * @param firstName new first name
     * @param lastName  new last name
     * @param username  new display username
     * @return the updated and saved {@link User}
     */
    User updateProfile(String email, String firstName, String lastName, String username);

    /**
     * Stores a new avatar object key for the authenticated user.
     *
     * <p>The caller is responsible for uploading the file to object storage before calling
     * this method. The stored key is later resolved to a presigned URL at read time.
     *
     * @param email     identifies the user to update
     * @param objectKey the MinIO object key (e.g. {@code "avatars/uuid.jpg"})
     * @return the updated and saved {@link User}
     */
    User updateAvatar(String email, String objectKey);
}
