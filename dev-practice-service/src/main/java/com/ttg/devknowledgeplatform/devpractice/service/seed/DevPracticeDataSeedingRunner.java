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
 * {@code DataSeedingRunner}.
 *
 * <p>Order is a real dependency, not style: {@link ProblemSeeder} resolves each problem's tags by
 * name, so {@link ProblemTagSeeder} must run first. Called explicitly in that order rather than as a
 * {@code List<Seeder>} loop, whose order would depend on bean registration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DevPracticeDataSeedingRunner implements ApplicationRunner {

    private final ProblemTagSeeder problemTagSeeder;
    private final ProblemSeeder problemSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting dev-practice data seeding...");
        problemTagSeeder.seed();
        problemSeeder.seed();
        log.info("Dev-practice data seeding complete.");
    }
}
