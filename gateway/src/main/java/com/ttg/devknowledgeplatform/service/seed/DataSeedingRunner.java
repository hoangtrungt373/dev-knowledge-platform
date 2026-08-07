package com.ttg.devknowledgeplatform.service.seed;

import com.ttg.devknowledgeplatform.content.service.seed.CategorySeeder;
import com.ttg.devknowledgeplatform.content.service.seed.QuestionAnswerSeeder;
import com.ttg.devknowledgeplatform.content.service.seed.TagSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the CSV data seeders once at application startup, in dependency order — categories, tags,
 * question-and-answer content (references categories/tags by id), then users. Gated by
 * {@code app.seed.enabled} (on for {@code local}/{@code docker}, off by default) so a
 * production-like profile never seeds unintentionally.
 *
 * <p>No longer seeds the friend graph/blocks/DM conversations — {@code FriendGraphSeeder}/
 * {@code UserBlockSeeder}/{@code DmThreadSeeder} moved fully into {@code social-service}'s own
 * seeding orchestration once that module was extracted into a standalone service with no Maven
 * dependency from this one (see that module's own {@code service.seed.DataSeedingRunner}).
 *
 * @author ttg
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeedingRunner implements ApplicationRunner {

    private final CategorySeeder categorySeeder;
    private final TagSeeder tagSeeder;
    private final QuestionAnswerSeeder questionAnswerSeeder;
    private final UserSeeder userSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting CSV data seeding...");
        categorySeeder.seed();
        tagSeeder.seed();
        questionAnswerSeeder.seed();
        userSeeder.seed();
        log.info("CSV data seeding complete.");
    }
}
