package com.ttg.devknowledgeplatform.identity.security.service.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.identity.entity.User;
import com.ttg.devknowledgeplatform.identity.enums.UserRole;
import com.ttg.devknowledgeplatform.identity.enums.UserStatus;
import com.ttg.devknowledgeplatform.identity.repository.UserRepository;
import com.ttg.devknowledgeplatform.identity.security.service.KeycloakAdminService;
import com.ttg.devknowledgeplatform.identity.security.service.KeycloakUserInfo;
import com.ttg.devknowledgeplatform.identity.security.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    // Never read/compared — Keycloak owns credentials entirely now. A real (non-null) value is
    // still required: User.password is a bean-validation @NotNull column that predates Keycloak.
    private static final String KEYCLOAK_MANAGED_PASSWORD_PLACEHOLDER = "KEYCLOAK_MANAGED";

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Override
    public User findOrCreateFromKeycloak(KeycloakUserInfo info) {
        User user = userRepository.findByKeycloakSubjectId(info.subject())
                .or(() -> userRepository.findByEmail(info.email()))
                .orElseGet(() -> User.builder()
                        .userUuid(UUID.randomUUID().toString())
                        .password(KEYCLOAK_MANAGED_PASSWORD_PLACEHOLDER)
                        .status(UserStatus.OFFLINE)
                        .build());

        boolean isNew = user.getId() == null;
        UserRole targetRole = info.admin() ? UserRole.ADMIN : UserRole.USER;

        // A brand-new brokered (Google/Facebook) login lands in Keycloak with username == email —
        // Keycloak's own default for a federated identity, which has no separate username concept
        // of its own; the same starting point local password-based accounts had before
        // KeycloakAdminServiceImpl.createUser began generating one from the email's local part.
        // Only ever attempted on the account's very first JIT-provisioning (isNew): a rename here
        // changes what Keycloak reports on every later login too, so repeating it on every request
        // would be pointless as well as wasteful. Best-effort — if Keycloak rejects it for any
        // reason, the account just keeps its email-shaped username rather than blocking login over
        // a cosmetic default; the caller can still rename it later via the Dashboard edit flow.
        String username = info.username();
        if (isNew && username.equals(info.email())) {
            try {
                username = keycloakAdminService.assignDerivedUsername(info.subject(), info.email());
            } catch (Exception e) {
                log.warn("Could not assign a derived username for new Keycloak subject {}: {}",
                        info.subject(), e.getMessage());
            }
        }

        boolean changed = isNew
                || !Objects.equals(user.getKeycloakSubjectId(), info.subject())
                || !Objects.equals(user.getEmail(), info.email())
                || !Objects.equals(user.getUsername(), username)
                || !Objects.equals(user.getFirstName(), info.firstName())
                || !Objects.equals(user.getLastName(), info.lastName())
                || user.getRole() != targetRole
                || !Objects.equals(user.getEmailVerified(), info.emailVerified())
                || !Boolean.TRUE.equals(user.getEnabled());

        if (!changed) {
            return user;
        }

        user.setKeycloakSubjectId(info.subject());
        user.setEmail(info.email());
        user.setUsername(username);
        user.setFirstName(info.firstName());
        user.setLastName(info.lastName());
        user.setRole(targetRole);
        user.setEmailVerified(info.emailVerified());
        user.setEnabled(true);

        User saved = userRepository.save(user);
        if (isNew) {
            log.info("JIT-provisioned new User from Keycloak subject={} email={} id={}",
                    info.subject(), info.email(), saved.getId());
        }
        return saved;
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Override
    public User resolveCurrentUser(CustomOAuth2User principal) {
        return Validator.notFound(userRepository.findByEmail(principal.getEmail()), CommonErrorCode.USER_NOT_FOUND);
    }

    @Override
    public User findByUserUuid(String userUuid) {
        return userRepository.findByUserUuid(userUuid).orElse(null);
    }

    @Override
    public Optional<User> findById(Integer userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> findByUserUuidOptional(String userUuid) {
        return userRepository.findByUserUuid(userUuid);
    }

    @Override
    public void updateStatus(Integer userId, UserStatus status) {
        userRepository.updateStatus(userId, status);
        log.info("Updated status for user id={} to {}", userId, status);
    }

    @Override
    public User updateProfile(String email, String firstName, String lastName, String username) {
        User user = Validator.notFound(userRepository.findByEmail(email), CommonErrorCode.USER_NOT_FOUND);
        user.setFirstName(firstName != null ? firstName.trim() : user.getFirstName());
        user.setLastName(lastName != null ? lastName.trim() : user.getLastName());
        if (username != null) {
            String trimmed = username.trim().toLowerCase();
            if (!trimmed.equals(user.getUsername())) {
                Validator.isFalse(userRepository.existsByUsernameAndIdNot(trimmed, user.getId()),
                        CommonErrorCode.USER_USERNAME_ALREADY_EXISTS, trimmed);
                // Keycloak owns preferred_username and re-syncs it into this row on every request
                // (see findOrCreateFromKeycloak) — renaming locally first would just get reverted
                // on the caller's next request, so Keycloak must be updated first. Called only on
                // an actual change to avoid an Admin API round trip on every firstName/lastName-only save.
                keycloakAdminService.updateUsername(user.getKeycloakSubjectId(), trimmed);
                user.setUsername(trimmed);
            }
        }
        return userRepository.save(user);
    }

    @Override
    public User updateAvatar(String email, String objectKey) {
        User user = Validator.notFound(userRepository.findByEmail(email), CommonErrorCode.USER_NOT_FOUND);
        user.setProfilePicture(objectKey);
        return userRepository.save(user);
    }

}
