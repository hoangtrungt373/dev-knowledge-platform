package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs this module's own CSV-based sample-catalog seeders once at application startup, in
 * dependency order — categories, then products (which need a category and at least one variant
 * to exist), then a first gallery image for a handful of featured products (which need a product
 * to exist). Gated by {@code app.seed.enabled} (off by default, enabled explicitly for the
 * `docker compose` stack) so a production-like profile never seeds unintentionally — same
 * property name and shape as {@code content-service}'s/{@code social-service}'s own
 * {@code DataSeedingRunner}.
 *
 * @author ttg
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class EcommerceDataSeedingRunner implements ApplicationRunner {

    private final ProductCategorySeeder productCategorySeeder;
    private final ProductSeeder productSeeder;
    private final ProductImageSeeder productImageSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting ecommerce-service CSV data seeding...");
        productCategorySeeder.seed();
        productSeeder.seed();
        productImageSeeder.seed();
        log.info("ecommerce-service CSV data seeding complete.");
    }
}
