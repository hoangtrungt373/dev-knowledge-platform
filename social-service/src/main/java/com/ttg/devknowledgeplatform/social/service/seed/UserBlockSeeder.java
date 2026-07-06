package com.ttg.devknowledgeplatform.social.service.seed;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;
import com.ttg.devknowledgeplatform.social.entity.UserBlock;
import com.ttg.devknowledgeplatform.social.repository.UserBlockRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seeds {@link UserBlock} rows from {@code data/csv/user-blocks.csv} (columns: blockerId,
 * blockedId), referencing {@link User} by {@code seedId} (see {@code UserSeeder}).
 *
 * <p>Idempotency: a row is skipped if a block already exists between the pair in either
 * direction ({@link UserBlockRepository#existsEitherDirection}) — blocking is directional in
 * production, but a seed file should never define both directions for the same pair, so checking
 * either direction is the safer guard against an accidental duplicate. No {@code seedId} needed
 * on {@code UserBlock} itself — same reasoning as {@code FriendGraphSeeder}, see Liquibase
 * {@code DKP-0016}.
 *
 * <p>Requires {@code UserSeeder} (api) to have already run.
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class UserBlockSeeder extends CsvSeeder<UserBlock> {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/user-blocks.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        User blocker = resolveUser(record.get("blockerId"));
        User blocked = resolveUser(record.get("blockedId"));
        return userBlockRepository.existsEitherDirection(blocker, blocked);
    }

    @Override
    protected UserBlock buildEntity(CSVRecord record) {
        User blocker = resolveUser(record.get("blockerId"));
        User blocked = resolveUser(record.get("blockedId"));
        return UserBlock.builder().blocker(blocker).blocked(blocked).build();
    }

    @Override
    protected void persist(UserBlock entity) {
        userBlockRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("blockerId") + " -> " + record.get("blockedId");
    }

    private User resolveUser(String seedId) {
        return userRepository.findBySeedId(seedId)
                .orElseThrow(() -> new IllegalStateException(
                        "user-blocks.csv references unknown user id '" + seedId + "' — run UserSeeder first"));
    }
}
