package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ProductSearchViewRepository#search} against a real Postgres —
 * covers what {@code ProductSearchServiceImplTest}'s mocked unit test structurally cannot: US-1.3's
 * actual {@code tsvector} keyword ranking and {@code pg_trgm} typo tolerance, and US-1.4's actual
 * price-range overlap, {@code inStockOnly}, and JSONB attribute-containment filtering. Every one
 * of these is real Postgres SQL syntax (native query, not JPQL) that a mocked repository can't
 * exercise at all.
 *
 * <p>Applies this module's own Liquibase migration SQL directly via JDBC in {@link #applySchema}
 * (not via Spring Boot's Liquibase autoconfiguration, which {@code @DataJpaTest} doesn't wire up)
 * — this guarantees the test runs against the *exact* real DDL (extension, generated column, GIN
 * indexes included), not a hand-rolled approximation of it. {@code spring.jpa.hibernate.ddl-auto}
 * stays at this app's own production value ({@code validate}), so a passing test also confirms the
 * entity mappings still agree with the real migration — the same check production startup does.
 *
 * <p><strong>Requires Docker.</strong> Unlike every other test in this module, this one cannot run
 * without a Docker daemon reachable from wherever {@code mvn test} executes.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductSearchViewRepositoryIT {

    private static final String MIGRATION_CLASSPATH_LOCATION =
            "com/ttg/devknowledgeplatform/ecommerce/database/sql/2026/0.0.2/"
                    + "202608040001__0.0.2__DKP-0023__add_ecommerce_catalog_tables.sql";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "ecommerce");
    }

    @BeforeAll
    static void applySchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            for (String sql : readMigrationStatements()) {
                statement.execute(sql);
            }
        }
    }

    private static List<String> readMigrationStatements() throws Exception {
        StringBuilder fullScript = new StringBuilder();
        try (InputStream in = ProductSearchViewRepositoryIT.class.getClassLoader()
                .getResourceAsStream(MIGRATION_CLASSPATH_LOCATION)) {
            if (in == null) {
                throw new IllegalStateException("Migration file not found on classpath: " + MIGRATION_CLASSPATH_LOCATION);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.strip().startsWith("--")) {
                        continue; // Liquibase/comment-only lines — no semicolons inside them in this file
                    }
                    fullScript.append(line).append('\n');
                }
            }
        }
        return List.of(fullScript.toString().split(";")).stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Autowired
    private ProductSearchViewRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private ProductCategory apparel;
    private ProductCategory drinkware;

    @BeforeEach
    void seedCategories() {
        apparel = persistCategory("Apparel", "apparel");
        drinkware = persistCategory("Drinkware", "drinkware");
    }

    private ProductCategory persistCategory(String name, String slug) {
        ProductCategory category = new ProductCategory();
        category.setName(name);
        category.setSlug(slug);
        return entityManager.persistFlushFind(category);
    }

    private Product persistProduct(String name, String slug, ProductCategory category) {
        Product product = new Product();
        product.setName(name);
        product.setSlug(slug);
        product.setActive(true);
        product.setProductCategory(category);
        return entityManager.persistFlushFind(product);
    }

    /** Inserts a {@code ProductSearchView} row directly — this table is normally written only by
     * the outbox projection handler, but a repository test only needs the row to exist, not the
     * outbox machinery that would otherwise produce it. */
    private ProductSearchView persistView(
            Product product, String name, String slug, ProductCategory category,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStock, String primaryImageStorageKey,
            String searchText, Map<String, List<String>> availableAttributes) {
        ProductSearchView view = new ProductSearchView();
        view.setProduct(product);
        view.setName(name);
        view.setSlug(slug);
        view.setProductCategoryId(category.getId());
        view.setCategoryName(category.getName());
        view.setMinPrice(minPrice);
        view.setMaxPrice(maxPrice);
        view.setInStock(inStock);
        view.setPrimaryImageStorageKey(primaryImageStorageKey);
        view.setSearchText(searchText);
        view.setAvailableAttributes(availableAttributes);
        return entityManager.persistFlushFind(view);
    }

    @Test
    void matchesByExactKeywordViaTsvector() {
        Product hoodie = persistProduct("Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel);
        persistView(hoodie, "Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel,
                BigDecimal.valueOf(54.99), BigDecimal.valueOf(54.99), true, null,
                "Ctrl+Z Hoodie Because everyone needs an undo button", Map.of());

        Page<ProductSearchView> results = repository.search(
                null, "hoodie", null, null, false, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(ProductSearchView::getName).containsExactly("Ctrl+Z Hoodie");
    }

    @Test
    void toleratesATypoViaTrigramSimilarity() {
        Product hoodie = persistProduct("Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel);
        persistView(hoodie, "Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel,
                BigDecimal.valueOf(54.99), BigDecimal.valueOf(54.99), true, null,
                "Ctrl+Z Hoodie Because everyone needs an undo button", Map.of());

        // "hoodei" (transposed letters) doesn't exact-token-match "hoodie" via tsvector at all —
        // only pg_trgm similarity should surface this row.
        Page<ProductSearchView> results = repository.search(
                null, "hoodei", null, null, false, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(ProductSearchView::getName).containsExactly("Ctrl+Z Hoodie");
    }

    @Test
    void excludesResultsBelowTheTrigramThreshold() {
        Product hoodie = persistProduct("Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel);
        persistView(hoodie, "Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel,
                BigDecimal.valueOf(54.99), BigDecimal.valueOf(54.99), true, null,
                "Ctrl+Z Hoodie Because everyone needs an undo button", Map.of());

        Page<ProductSearchView> results = repository.search(
                null, "xyzzy plugh completely unrelated", null, null, false, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void filtersByCategory() {
        Product hoodie = persistProduct("Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel);
        persistView(hoodie, "Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "Ctrl+Z Hoodie", Map.of());
        Product mug = persistProduct("Console Mug", "console-mug", drinkware);
        persistView(mug, "Console Mug", "console-mug", drinkware,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "Console Mug", Map.of());

        Page<ProductSearchView> results = repository.search(
                drinkware.getId(), null, null, null, false, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(ProductSearchView::getName).containsExactly("Console Mug");
    }

    @Test
    void priceFilterMatchesOnRangeOverlapNotExactEquality() {
        Product hoodie = persistProduct("Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel);
        // This product's variants span $40-$60 — a $50-$70 filter overlaps (its $60 falls inside),
        // even though no single variant is priced exactly within [50,70] at both ends.
        persistView(hoodie, "Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel,
                BigDecimal.valueOf(40), BigDecimal.valueOf(60), true, null, "Ctrl+Z Hoodie", Map.of());

        Page<ProductSearchView> results = repository.search(
                null, null, BigDecimal.valueOf(50), BigDecimal.valueOf(70), false, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(ProductSearchView::getName).containsExactly("Ctrl+Z Hoodie");
    }

    @Test
    void priceFilterExcludesARangeThatDoesNotOverlapAtAll() {
        Product hoodie = persistProduct("Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel);
        persistView(hoodie, "Ctrl+Z Hoodie", "ctrl-z-hoodie", apparel,
                BigDecimal.valueOf(40), BigDecimal.valueOf(60), true, null, "Ctrl+Z Hoodie", Map.of());

        Page<ProductSearchView> results = repository.search(
                null, null, BigDecimal.valueOf(100), BigDecimal.valueOf(200), false, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void inStockOnlyExcludesOutOfStockProductsWhenEnabled() {
        Product inStockProduct = persistProduct("In Stock Tee", "in-stock-tee", apparel);
        persistView(inStockProduct, "In Stock Tee", "in-stock-tee", apparel,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "In Stock Tee", Map.of());
        Product outOfStockProduct = persistProduct("Sold Out Tee", "sold-out-tee", apparel);
        persistView(outOfStockProduct, "Sold Out Tee", "sold-out-tee", apparel,
                BigDecimal.TEN, BigDecimal.TEN, false, null, "Sold Out Tee", Map.of());

        Page<ProductSearchView> results = repository.search(
                null, null, null, null, true, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(ProductSearchView::getName).containsExactly("In Stock Tee");
    }

    @Test
    void inStockOnlyFalseIncludesBothInStockAndOutOfStockProducts() {
        Product inStockProduct = persistProduct("In Stock Tee", "in-stock-tee", apparel);
        persistView(inStockProduct, "In Stock Tee", "in-stock-tee", apparel,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "In Stock Tee", Map.of());
        Product outOfStockProduct = persistProduct("Sold Out Tee", "sold-out-tee", apparel);
        persistView(outOfStockProduct, "Sold Out Tee", "sold-out-tee", apparel,
                BigDecimal.TEN, BigDecimal.TEN, false, null, "Sold Out Tee", Map.of());

        Page<ProductSearchView> results = repository.search(
                null, null, null, null, false, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(ProductSearchView::getName)
                .containsExactlyInAnyOrder("In Stock Tee", "Sold Out Tee");
    }

    @Test
    void attributeFilterMatchesOnJsonbContainmentAcrossMultipleKeys() {
        Product matching = persistProduct("404 Not Found T-Shirt", "404-not-found-t-shirt", apparel);
        persistView(matching, "404 Not Found T-Shirt", "404-not-found-t-shirt", apparel,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "404 Not Found T-Shirt",
                Map.of("size", List.of("S", "M", "L"), "color", List.of("Black", "White")));
        Product nonMatching = persistProduct("Compile Time Socks", "compile-time-socks", apparel);
        persistView(nonMatching, "Compile Time Socks", "compile-time-socks", apparel,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "Compile Time Socks",
                Map.of("size", List.of("S-M", "L-XL")));

        // AND across keys: both size=M and color=Black must be satisfied.
        String attributeFilterJson = "{\"size\":[\"M\"],\"color\":[\"Black\"]}";
        Page<ProductSearchView> results = repository.search(
                null, null, null, null, false, attributeFilterJson, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(ProductSearchView::getName).containsExactly("404 Not Found T-Shirt");
    }

    @Test
    void attributeFilterExcludesAProductMissingOneOfTheRequestedKeys() {
        Product product = persistProduct("Compile Time Socks", "compile-time-socks", apparel);
        persistView(product, "Compile Time Socks", "compile-time-socks", apparel,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "Compile Time Socks",
                Map.of("size", List.of("S-M", "L-XL"))); // no "color" key at all

        String attributeFilterJson = "{\"size\":[\"S-M\"],\"color\":[\"Black\"]}";
        Page<ProductSearchView> results = repository.search(
                null, null, null, null, false, attributeFilterJson, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void withoutAKeywordResultsAreOrderedByRecencyNewestFirst() {
        Product older = persistProduct("Older Product", "older-product", apparel);
        persistView(older, "Older Product", "older-product", apparel,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "Older Product", Map.of());
        Product newer = persistProduct("Newer Product", "newer-product", apparel);
        persistView(newer, "Newer Product", "newer-product", apparel,
                BigDecimal.TEN, BigDecimal.TEN, true, null, "Newer Product", Map.of());

        Page<ProductSearchView> results = repository.search(
                null, null, null, null, false, null, 0.3, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(ProductSearchView::getName)
                .containsExactly("Newer Product", "Older Product");
    }
}
