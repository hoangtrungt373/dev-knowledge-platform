package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs this module's own CSV-based sample-catalog seeders once at application startup, in
 * dependency order — categories and tags (independent of each other, but both needed before a
 * product can reference either by name), then the "Option B" global attribute registry
 * ({@link ProductAttributeSeeder}, independent of categories/tags/products) and each category's
 * own attribute-schema assignment ({@link ProductCategoryAttributeSeeder}, which needs both
 * categories and attributes to already exist), then products (which need a category and at least
 * one variant to exist, may optionally reference already-seeded tags, and — now that a category
 * may carry an attribute schema — must already satisfy whichever schema its own category was just
 * assigned; see {@link ProductSeeder}'s own Javadoc), then a first gallery image for a handful of
 * featured products (which need a product to exist). Coupons are independent of every other seeder
 * here (a {@code Coupon} has no foreign key onto a product/category/tag — see
 * {@link CouponSeeder}'s own Javadoc) and so run last purely by convention, not because anything
 * depends on them coming after. Gated by {@code app.seed.enabled} (off by default,
 * enabled explicitly for the `docker compose` stack) so a production-like profile never seeds
 * unintentionally — same property name and shape as {@code content-service}'s/
 * {@code social-service}'s own {@code DataSeedingRunner}.
 *
 * @author ttg
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class EcommerceDataSeedingRunner implements ApplicationRunner {

    private final ProductCategorySeeder productCategorySeeder;
    private final ProductTagSeeder productTagSeeder;
    private final ProductAttributeSeeder productAttributeSeeder;
    private final ProductCategoryAttributeSeeder productCategoryAttributeSeeder;
    private final ProductSeeder productSeeder;
    private final ProductImageSeeder productImageSeeder;
    private final CouponSeeder couponSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting ecommerce-service CSV data seeding...");
        productCategorySeeder.seed();
        productTagSeeder.seed();
        productAttributeSeeder.seed();
        productCategoryAttributeSeeder.seed();
        productSeeder.seed();
        productImageSeeder.seed();
        couponSeeder.seed();
        log.info("ecommerce-service CSV data seeding complete.");
    }
}
