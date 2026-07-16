package com.ttg.devknowledgeplatform.service.seed;

import com.ttg.devknowledgeplatform.content.service.seed.CategorySeeder;
import com.ttg.devknowledgeplatform.content.service.seed.QuestionAnswerSeeder;
import com.ttg.devknowledgeplatform.content.service.seed.TagSeeder;
import com.ttg.devknowledgeplatform.identity.service.seed.UserSeeder;
import com.ttg.devknowledgeplatform.social.service.seed.DmThreadSeeder;
import com.ttg.devknowledgeplatform.social.service.seed.FriendGraphSeeder;
import com.ttg.devknowledgeplatform.social.service.seed.UserBlockSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the CSV data seeders once at application startup, in dependency order — categories, tags,
 * question-and-answer content (references categories/tags by id), then users, then the friend
 * graph and blocks (both reference users by id), then sample DM conversations (one per accepted
 * friendship, so the Messages GUI has data to show). Gated by {@code app.seed.enabled} (on for
 * {@code local}/{@code docker}, off by default) so a production-like profile never seeds
 * unintentionally.
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
    private final FriendGraphSeeder friendGraphSeeder;
    private final DmThreadSeeder dmThreadSeeder;
    private final UserBlockSeeder userBlockSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting CSV data seeding...");
        categorySeeder.seed();
        tagSeeder.seed();
        questionAnswerSeeder.seed();
        userSeeder.seed();
        friendGraphSeeder.seed();
        dmThreadSeeder.seed();
        userBlockSeeder.seed();
        log.info("CSV data seeding complete.");
    }
}
