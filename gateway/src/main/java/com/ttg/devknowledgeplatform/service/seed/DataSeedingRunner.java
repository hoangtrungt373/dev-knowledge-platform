package com.ttg.devknowledgeplatform.service.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs this app's own CSV data seeders once at application startup. Gated by
 * {@code app.seed.enabled} (on for {@code local}/{@code docker}, off by default) so a
 * production-like profile never seeds unintentionally.
 *
 * <p>Only seeds {@code product.USER} now — {@code CategorySeeder}/{@code TagSeeder}/
 * {@code QuestionAnswerSeeder} moved fully into {@code content-service}'s own seeding
 * orchestration once that module was extracted into a standalone service with no Maven dependency
 * from this one (see that module's own {@code service.seed.DataSeedingRunner}), the same way
 * {@code FriendGraphSeeder}/{@code UserBlockSeeder}/{@code DmThreadSeeder} moved to
 * {@code social-service}'s own runner before it.
 *
 * @author ttg
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeedingRunner implements ApplicationRunner {

    private final UserSeeder userSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting CSV data seeding...");
        userSeeder.seed();
        log.info("CSV data seeding complete.");
    }
}
