package com.ttg.devknowledgeplatform.content.service.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs this module's own CSV/Markdown data seeders once at application startup, in dependency
 * order — categories, tags, then question-and-answer content (references categories/tags by id).
 * Gated by {@code app.seed.enabled} (on for {@code local}/{@code docker}, off by default) so a
 * production-like profile never seeds unintentionally.
 *
 * <p>Moved here from {@code gateway}'s own {@code DataSeedingRunner} as part of this module's
 * standalone-service extraction — {@code gateway} no longer has a Maven dependency on this module,
 * so it can no longer orchestrate these three seeders itself (see {@code gateway}'s own runner,
 * narrowed to {@code UserSeeder} only). Same pattern {@code social-service}'s own
 * {@code DataSeedingRunner} already established for its own seeders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeedingRunner implements ApplicationRunner {

    private final CategorySeeder categorySeeder;
    private final TagSeeder tagSeeder;
    private final QuestionAnswerSeeder questionAnswerSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting CSV data seeding...");
        categorySeeder.seed();
        tagSeeder.seed();
        questionAnswerSeeder.seed();
        log.info("CSV data seeding complete.");
    }
}
