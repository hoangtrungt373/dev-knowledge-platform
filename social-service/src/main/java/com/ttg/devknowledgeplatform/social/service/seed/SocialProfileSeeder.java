package com.ttg.devknowledgeplatform.social.service.seed;

import java.util.UUID;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;
import com.ttg.devknowledgeplatform.social.entity.SocialProfile;
import com.ttg.devknowledgeplatform.social.enums.ProfileStatus;
import com.ttg.devknowledgeplatform.social.repository.SocialProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seeds {@link SocialProfile} rows from this module's own {@code data/csv/users.csv} (columns:
 * id, email, username, firstName, lastName) — this module's own copy of the same 20 demo accounts
 * {@code gateway}'s {@code UserSeeder} seeds into {@code product.USER}, duplicated here (same
 * {@code id}/seed-key values, same rows) so {@link FriendGraphSeeder}/{@link UserBlockSeeder}/
 * {@link DmThreadSeeder} keep working unchanged after this module's extraction into a standalone
 * service.
 *
 * <p>Necessary because seed accounts have no real Keycloak identity to JIT-provision from — the
 * only other way a {@code SocialProfile} row is ever created (see
 * {@code security.KeycloakJwtAuthenticationConverter}) — and {@code social-service} is a separate
 * deployable now with no access to {@code gateway}'s resources/classpath at runtime, so it can't
 * share that module's CSV file or seeded rows directly. Each service's seeded row for "the same"
 * demo person is therefore an independent copy, exactly like a real Keycloak login would
 * independently JIT-provision one in each service — this seeder just does it up front instead.
 *
 * <p>Unlike {@code gateway}'s {@code UserSeeder}, this entity has no {@code password}/
 * {@code provider}/{@code emailVerified}/{@code enabled} columns to fill — {@link SocialProfile}
 * never had an auth-lifecycle concern (see that class's Javadoc), so seeded rows here are exactly
 * as complete as a real JIT-provisioned one.
 *
 * <p>Extends {@code infra}'s generic {@link CsvSeeder} Template Method — same one this module's own
 * {@link UserBlockSeeder} and {@code gateway}'s {@code UserSeeder} use.
 */
@Component
@RequiredArgsConstructor
public class SocialProfileSeeder extends CsvSeeder<SocialProfile> {

    private final SocialProfileRepository socialProfileRepository;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/users.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        String seedId = record.get("id");
        String email = record.get("email");
        return socialProfileRepository.findBySeedId(seedId)
                .map(existing -> {
                    if (!existing.getEmail().equalsIgnoreCase(email)) {
                        throw new IllegalStateException("users.csv id '" + seedId
                                + "' is already used by profile '" + existing.getEmail()
                                + "' but this row now has email '" + email
                                + "' — an id must never be reused for a different user");
                    }
                    return true;
                })
                .orElse(false);
    }

    @Override
    protected SocialProfile buildEntity(CSVRecord record) {
        return SocialProfile.builder()
                .seedId(record.get("id"))
                .profileUuid(UUID.randomUUID().toString())
                .email(record.get("email"))
                .username(record.get("username"))
                .firstName(record.get("firstName"))
                .lastName(record.get("lastName"))
                .status(ProfileStatus.OFFLINE)
                .build();
    }

    @Override
    protected void persist(SocialProfile entity) {
        socialProfileRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("id");
    }
}
