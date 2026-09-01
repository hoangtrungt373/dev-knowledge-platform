package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductTagRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductService;
import com.ttg.devknowledgeplatform.infra.service.seed.Seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds {@link Product}s (with their variants) from {@code data/csv/products.csv} +
 * {@code data/csv/product_variants.csv} joined by product name.
 *
 * <p><strong>Implements {@link Seeder} directly rather than extending {@code infra}'s
 * {@code CsvSeeder<T>}</strong>, for two reasons: this seeder joins two files (one row in
 * {@code products.csv} needs every matching row in {@code product_variants.csv} before it can
 * build one {@code Create} command), and — more importantly — it routes through
 * {@link ProductService#create}/{@link ProductService#deactivate} rather than
 * {@code ProductRepository.save} directly. {@code ProductCategorySeeder} bypasses the service
 * layer the same way {@code content-service}'s {@code CategorySeeder} does, but a product
 * genuinely needs the service layer here: {@code ProductServiceImpl.create}/{@code deactivate}
 * both publish a {@code PRODUCT_CHANGED} outbox event, which is what gets a seeded product into
 * {@code ProductSearchView} at all — inserting rows directly would leave every seeded product
 * invisible to the public browse/search endpoints (US-1.1/1.3/1.4) until something else happened
 * to touch them, since nothing else re-derives that CQRS read model.
 *
 * <p>Idempotency key is {@code name} (see {@link ProductCategorySeeder}'s Javadoc for why this
 * isn't a decoupled {@code seedId} for this first-pass sample catalog); {@code products.csv}'
 * optional {@code active} column (blank/{@code true} by default) lets one seed row demonstrate
 * US-1.7's deactivate-on-create for free, without a second pass. {@code products.csv}'s optional
 * {@code tagNames} column (semicolon-joined, same compact inline convention as
 * {@code product_variants.csv}'s {@code attributes} cell) resolves each name to a
 * {@link ProductTag} id via {@link ProductTagRepository} and feeds the result straight into
 * {@link ProductCommands.Create#tagIds()} — assignment itself is exercised for free through the
 * same {@code create} call that already handles variants/images, no separate pass needed (unlike
 * {@link ProductTagSeeder}, which must run first so every referenced name already exists).
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductSeeder implements Seeder {

    private static final String PRODUCTS_CSV = "data/csv/products.csv";
    private static final String VARIANTS_CSV = "data/csv/product_variants.csv";

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductTagRepository productTagRepository;
    private final ProductService productService;

    @Override
    public int seed() {
        Map<String, List<CSVRecord>> variantsByProduct = readVariantsGroupedByProductName();

        int inserted = 0;
        int skipped = 0;
        for (CSVRecord record : readCsv(PRODUCTS_CSV)) {
            String name = record.get("name");
            if (productRepository.findByNameIgnoreCase(name).isPresent()) {
                skipped++;
                log.debug("ProductSeeder: skipping existing product '{}'", name);
                continue;
            }

            List<CSVRecord> variantRows = variantsByProduct.get(name);
            if (variantRows == null || variantRows.isEmpty()) {
                throw new IllegalStateException("products.csv row '" + name
                        + "' has no matching rows in product_variants.csv — every product needs at least one variant");
            }

            String categoryName = record.get("categoryName");
            ProductCategory category = productCategoryRepository.findByNameIgnoreCase(categoryName)
                    .orElseThrow(() -> new IllegalStateException("products.csv row '" + name
                            + "' references unknown category '" + categoryName
                            + "' — seed product_categories.csv first"));

            String description = record.get("description");
            List<ProductCommands.VariantInput> variants = variantRows.stream()
                    .map(ProductSeeder::toVariantInput)
                    .toList();

            ProductCommands.Create command = new ProductCommands.Create(
                    name,
                    description == null || description.isBlank() ? null : description,
                    category.getId(),
                    variants,
                    List.of(),
                    resolveTagIds(record, name));
            Product created = productService.create(command);

            if (isExplicitlyInactive(record)) {
                productService.deactivate(created.getId());
            }

            inserted++;
        }

        log.info("ProductSeeder: inserted {} product(s), skipped {} already-present product(s)", inserted, skipped);
        return inserted;
    }

    /** {@code tagNames} is optional (the column, or the cell, may be blank) — an untagged product
     * is a perfectly normal seed row, not an error. Unlike {@code categoryName}'s lookup above, an
     * unknown tag name fails loudly rather than being silently skipped, same reasoning: a typo'd
     * name in seed data should surface immediately, not produce a silently under-tagged product. */
    private Set<Integer> resolveTagIds(CSVRecord record, String productName) {
        if (!record.isMapped("tagNames")) {
            return Set.of();
        }
        String raw = record.get("tagNames");
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        Set<Integer> tagIds = new LinkedHashSet<>();
        for (String tagName : raw.split(";")) {
            String trimmed = tagName.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ProductTag tag = productTagRepository.findByNameIgnoreCase(trimmed)
                    .orElseThrow(() -> new IllegalStateException("products.csv row '" + productName
                            + "' references unknown tag '" + trimmed
                            + "' — seed product_tags.csv first"));
            tagIds.add(tag.getId());
        }
        return tagIds;
    }

    private static boolean isExplicitlyInactive(CSVRecord record) {
        if (!record.isMapped("active")) {
            return false;
        }
        String active = record.get("active");
        return active != null && "false".equalsIgnoreCase(active.trim());
    }

    private static ProductCommands.VariantInput toVariantInput(CSVRecord record) {
        return new ProductCommands.VariantInput(
                record.get("sku"),
                new BigDecimal(record.get("price")),
                Integer.parseInt(record.get("stockQuantity")),
                parseAttributes(record.get("attributes")));
    }

    /** {@code key1=value1;key2=value2} — a compact inline format since a CSV cell can't hold a nested map. */
    private static Map<String, String> parseAttributes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String pair : raw.split(";")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                attributes.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return attributes;
    }

    private Map<String, List<CSVRecord>> readVariantsGroupedByProductName() {
        Map<String, List<CSVRecord>> byProduct = new LinkedHashMap<>();
        for (CSVRecord record : readCsv(VARIANTS_CSV)) {
            byProduct.computeIfAbsent(record.get("productName"), k -> new ArrayList<>()).add(record);
        }
        return byProduct;
    }

    /** Same read shape as {@code infra}'s {@code CsvSeeder}, duplicated here rather than reused —
     * that class's {@code seed()} is {@code final} and owns the whole read-and-persist loop, which
     * doesn't fit a seeder that needs to read two files before persisting anything. */
    private static Iterable<CSVRecord> readCsv(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();
        try (InputStream in = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seed CSV: " + classpathLocation, e);
        }
    }
}
