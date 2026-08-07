package com.ttg.devknowledgeplatform.social.service.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs this module's own CSV data seeders once at application startup, in dependency order —
 * profiles, then the friend graph and blocks (both reference profiles by seed id), then sample DM
 * conversations (one per accepted friendship, so the Messages GUI has data to show). Gated by
 * {@code app.seed.enabled} (on for {@code docker}, off by default) — same convention
 * {@code gateway}'s own {@code DataSeedingRunner} uses.
 *
 * <p>This module's own runner, not a continuation of {@code gateway}'s — {@code gateway}'s
 * {@code DataSeedingRunner} used to inject {@link FriendGraphSeeder}/{@link DmThreadSeeder}/
 * {@link UserBlockSeeder} directly (all three already lived in this module even before
 * extraction), which stopped compiling once {@code gateway} dropped its Maven dependency on this
 * module. Those three seeders — and the CSV files they read — moved fully into this module's own
 * orchestration; {@code gateway}'s runner no longer references any of them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeedingRunner implements ApplicationRunner {

    private final SocialProfileSeeder socialProfileSeeder;
    private final FriendGraphSeeder friendGraphSeeder;
    private final UserBlockSeeder userBlockSeeder;
    private final DmThreadSeeder dmThreadSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting CSV data seeding...");
        socialProfileSeeder.seed();
        friendGraphSeeder.seed();
        userBlockSeeder.seed();
        dmThreadSeeder.seed();
        log.info("CSV data seeding complete.");
    }
}
