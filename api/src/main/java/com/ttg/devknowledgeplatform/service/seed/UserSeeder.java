package com.ttg.devknowledgeplatform.service.seed;

import java.util.UUID;

import org.apache.commons.csv.CSVRecord;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;

import lombok.RequiredArgsConstructor;

/**
 * Seeds {@link User} rows from {@code data/csv/users.csv} (columns: id, email, username,
 * firstName, lastName) — 20 sample accounts for exercising the Friend Management GUI end to end.
 *
 * <p>Every seeded user is a {@code LOCAL}-provider account with {@code emailVerified=true} and
 * {@code enabled=true}, sharing one known demo password ({@link #DEMO_PASSWORD}) so any of them
 * can actually be logged into via the normal login form — these are login-able test accounts,
 * not just rows to look at in the database.
 *
 * <p>{@code id} is a permanent, seed-file-only identifier (persisted as {@code User.seedId},
 * never shown to end users) — the sole idempotency key, deliberately decoupled from
 * {@code email}/{@code username} for the same reason as {@code CategorySeeder}: those fields are
 * human-editable ({@code UserServiceImpl.updateProfile}) and must stay free to change without
 * risking a duplicate insert on the next seeding run. {@code social-service}'s
 * {@code FriendGraphSeeder}/{@code UserBlockSeeder} reference users by this same {@code id},
 * resolved via {@link UserRepository#findBySeedId}.
 *
 * <p>Extends {@code infra}'s generic {@link CsvSeeder} Template Method — the same one
 * {@code content-service}'s {@code CategorySeeder}/{@code TagSeeder} and {@code social-service}'s
 * {@code UserBlockSeeder} use.
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class UserSeeder extends CsvSeeder<User> {

    /** Plaintext for the shared BCrypt hash every seeded user gets — LOCAL DEV ONLY. */
    public static final String DEMO_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/users.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        String seedId = record.get("id");
        String email = record.get("email");
        return userRepository.findBySeedId(seedId)
                .map(existing -> {
                    if (!existing.getEmail().equalsIgnoreCase(email)) {
                        throw new IllegalStateException("users.csv id '" + seedId
                                + "' is already used by user '" + existing.getEmail()
                                + "' but this row now has email '" + email
                                + "' — an id must never be reused for a different user");
                    }
                    return true;
                })
                .orElse(false);
    }

    @Override
    protected User buildEntity(CSVRecord record) {
        String seedId = record.get("id");
        String email = record.get("email");
        String username = record.get("username");

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("users.csv id '" + seedId + "' has email '" + email
                    + "', but a user with that email already exists (created by a different id, or a real signup) — "
                    + "rename one of them to resolve the conflict");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("users.csv id '" + seedId + "' has username '" + username
                    + "', but a user with that username already exists — rename one of them to resolve the conflict");
        }

        return User.builder()
                .seedId(seedId)
                .userUuid(UUID.randomUUID().toString())
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .firstName(record.get("firstName"))
                .lastName(record.get("lastName"))
                .provider(UserProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .status(UserStatus.OFFLINE)
                .build();
    }

    @Override
    protected void persist(User entity) {
        userRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("id");
    }
}
