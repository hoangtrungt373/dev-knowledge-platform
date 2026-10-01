package com.ttg.devknowledgeplatform.devpractice.service.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs this service's seeders once the application has started — only when
 * {@code app.seed.enabled=true} ({@code APP_SEED_ENABLED}), same switch as {@code content-service}'s
 * {@code DataSeedingRunner}. Problem tags aren't seeded here: Liquibase {@code DKP-0055} already
 * inserts the topic catalog, which {@link ProblemSeeder} resolves tag names against.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DevPracticeDataSeedingRunner implements ApplicationRunner {

    private final ProblemSeeder problemSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting dev-practice data seeding...");
        problemSeeder.seed();
        log.info("Dev-practice data seeding complete.");
    }
}
