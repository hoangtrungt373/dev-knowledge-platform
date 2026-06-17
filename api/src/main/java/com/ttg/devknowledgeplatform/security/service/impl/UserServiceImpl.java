package com.ttg.devknowledgeplatform.security.service.impl;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.OAuth2UserInfo;
import com.ttg.devknowledgeplatform.repository.UserRepository;
import com.ttg.devknowledgeplatform.security.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 4;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User registerOAuth2User(OAuth2UserInfo userInfo, UserProvider provider) {
        log.info("Registering new OAuth2 user: {} with provider: {}", userInfo.getEmail(), provider);

        User user = User.builder()
                .userUuid(UUID.randomUUID().toString())
                .username(generateUsername(userInfo.getName()))
                .email(userInfo.getEmail())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .firstName(extractFirstName(userInfo.getName()))
                .lastName(extractLastName(userInfo.getName()))
                .profilePicture(userInfo.getImageUrl())
                .provider(provider)
                .providerId(userInfo.getId())
                .emailVerified(true)
                .enabled(true)
                .status(UserStatus.OFFLINE)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered OAuth2 user: {} (id={}, uuid={})", saved.getEmail(), saved.getId(), saved.getUserUuid());
        return saved;
    }

    @Override
    public User updateOAuth2User(User existingUser, OAuth2UserInfo userInfo) {
        log.info("Updating OAuth2 user: {} (id={})", existingUser.getEmail(), existingUser.getId());

        existingUser.setUsername(generateUsername(userInfo.getName()));
        existingUser.setFirstName(extractFirstName(userInfo.getName()));
        existingUser.setLastName(extractLastName(userInfo.getName()));
        existingUser.setProfilePicture(userInfo.getImageUrl());
        existingUser.setUsrLastModification("system");

        User updated = userRepository.save(existingUser);
        log.info("Updated OAuth2 user: {} (id={})", updated.getEmail(), updated.getId());
        return updated;
    }

    @Override
    public User registerLocalUser(String email, String firstName, String lastName, String rawPassword) {
        log.info("Registering new local user: {}", email);
        User user = User.builder()
                .userUuid(UUID.randomUUID().toString())
                .email(email)
                .username(generateUsername(firstName + " " + lastName))
                .password(passwordEncoder.encode(rawPassword))
                .firstName(firstName)
                .lastName(lastName)
                .provider(UserProvider.LOCAL)
                .emailVerified(false)
                .enabled(true)
                .status(UserStatus.OFFLINE)
                .build();
        User saved = userRepository.save(user);
        log.info("Registered local user id={} uuid={}", saved.getId(), saved.getUserUuid());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public User findByUserUuid(String userUuid) {
        return userRepository.findByUserUuid(userUuid).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Integer userId) {
        return userRepository.findById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUserUuidOptional(String userUuid) {
        return userRepository.findByUserUuid(userUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public User findByProviderAndProviderId(UserProvider provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId).orElse(null);
    }

    @Override
    public void updateStatus(Integer userId, UserStatus status) {
        userRepository.updateStatus(userId, status);
        log.info("Updated status for user id={} to {}", userId, status);
    }

    @Override
    public User updateProfile(String email, String firstName, String lastName, String username) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        user.setFirstName(firstName != null ? firstName.trim() : user.getFirstName());
        user.setLastName(lastName != null ? lastName.trim() : user.getLastName());
        if (username != null) {
            String trimmed = username.trim().toLowerCase();
            if (userRepository.existsByUsernameAndIdNot(trimmed, user.getId())) {
                throw new ApiException(ErrorCode.USER_USERNAME_ALREADY_EXISTS, "Username '" + trimmed + "' is already taken");
            }
            user.setUsername(trimmed);
        }
        return userRepository.save(user);
    }

    @Override
    public User updateAvatar(String email, String objectKey) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        user.setProfilePicture(objectKey);
        return userRepository.save(user);
    }

    @Override
    public User enableUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        user.setEnabled(true);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private String generateUsername(String fullName) {
        String base = (fullName == null || fullName.trim().isEmpty())
                ? "user"
                : fullName.toLowerCase()
                        .replaceAll("[^a-z0-9]", "_")
                        .replaceAll("_+", "_")
                        .replaceAll("^_|_$", "");
        String username;
        do {
            username = base + "_" + randomSuffix();
        } while (userRepository.existsByUsername(username));
        return username;
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(SUFFIX_CHARS.charAt(RANDOM.nextInt(SUFFIX_CHARS.length())));
        }
        return sb.toString();
    }

    private static String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return null;
        return fullName.trim().split("\\s+")[0];
    }

    private static String extractLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return null;
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 1 ? String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) : null;
    }
}
