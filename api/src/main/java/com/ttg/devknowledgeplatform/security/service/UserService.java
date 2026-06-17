package com.ttg.devknowledgeplatform.security.service;

import java.util.Optional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.dto.OAuth2UserInfo;

/**
 * Manages the lifecycle of {@link User} accounts for both local and OAuth2 sign-in flows.
 *
 * <p>Lookup methods come in two flavours: throwing variants (no {@code Optional}) that
 * raise a {@code ResourceNotFoundException} when the user is absent, and {@code Optional}
 * variants for call sites where absence is a handled case rather than an error.
 */
public interface UserService {

    /**
     * Creates a new user from an OAuth2 provider login.
     *
     * @param userInfo the normalised profile data extracted from the provider
     * @param provider the OAuth2 provider (e.g. {@code GOOGLE})
     * @return the newly persisted {@link User}
     */
    User registerOAuth2User(OAuth2UserInfo userInfo, UserProvider provider);

    /**
     * Synchronises a returning OAuth2 user's profile with the latest data from the provider.
     *
     * <p>Only fields that may change between logins (e.g. name, avatar URL) are updated.
     *
     * @param existingUser the user record already in the database
     * @param userInfo     fresh profile data from the provider
     * @return the updated and saved {@link User}
     */
    User updateOAuth2User(User existingUser, OAuth2UserInfo userInfo);

    /**
     * Creates a new user with email/password credentials.
     *
     * <p>The password is stored hashed; the account is initially unverified until
     * the user completes OTP email verification.
     *
     * @param email       the registration email address
     * @param firstName   the user's first name
     * @param lastName    the user's last name
     * @param rawPassword the plaintext password (hashed before persisting)
     * @return the newly persisted {@link User}
     */
    User registerLocalUser(String email, String firstName, String lastName, String rawPassword);

    /**
     * Returns the user with the given email address, throwing if not found.
     *
     * @param email the email to look up
     * @return the matching {@link User}
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no user exists with that email
     */
    User findByEmail(String email);

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
     * Looks up a user by their OAuth2 provider and provider-issued subject identifier.
     *
     * <p>Used during the OAuth2 login flow to detect returning users.
     *
     * @param provider   the OAuth2 provider
     * @param providerId the subject identifier issued by the provider
     * @return the matching {@link User}
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no match is found
     */
    User findByProviderAndProviderId(UserProvider provider, String providerId);

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

    /**
     * Marks the user's email as verified and activates the account.
     *
     * <p>Called after a successful OTP verification during local registration.
     *
     * @param email the email address of the user to activate
     * @return the updated and saved {@link User}
     */
    User enableUser(String email);
}
