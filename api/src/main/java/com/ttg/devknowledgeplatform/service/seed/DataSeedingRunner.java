package com.ttg.devknowledgeplatform.service.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the CSV data seeders once at application startup, in dependency order — categories,
 * then tags, then interview questions — since interview question rows reference both by slug.
 * Gated by {@code app.seed.enabled} (on for {@code local}/{@code docker}, off by default) so a
 * production-like profile never seeds unintentionally.
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
    private final InterviewQuestionSeeder interviewQuestionSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting CSV data seeding...");
        categorySeeder.seed();
        tagSeeder.seed();
        interviewQuestionSeeder.seed();
        log.info("CSV data seeding complete.");
    }
}
