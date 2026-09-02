package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategoryAttribute;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductAttributeRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.infra.service.seed.Seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds each {@link ProductCategory}'s own attribute schema — the "Option B" global attribute
 * registry's category assignment — from {@code data/csv/product_category_attributes.csv}
 * (columns: {@code categoryName}, {@code attributeName}, {@code required}), joined against
 * already-seeded {@link ProductCategory}/{@link ProductAttribute} rows by name.
 *
 * <p><strong>Implements {@link Seeder} directly rather than extending {@code infra}'s
 * {@code CsvSeeder<T>}</strong>, for the same reason {@link ProductSeeder} does: one unit of work
 * here is a whole category's *complete* attribute list, not one CSV row — {@code
 * ProductCategoryServiceImpl.applyCategoryAttributes} (and this seeder, mirroring it) always
 * clears and rebuilds the entire {@link ProductCategory#getCategoryAttributes()} collection at
 * once, with each assignment's list position becoming its {@code displayOrder} — so every row for
 * one category must be gathered before anything is persisted, not persisted one row at a time.
 *
 * <p><strong>Bypasses {@code ProductCategoryService} and persists directly via the repository</strong>,
 * mirroring {@code ProductCategorySeeder}/{@code ProductTagSeeder} — a category's attribute schema
 * has no outbox event / read-model side effect either. {@link ProductCategoryAttribute} itself is
 * never saved through its own repository regardless (see that entity's own Javadoc) — assignments
 * are added straight to {@code category.getCategoryAttributes()} and persisted by cascade when the
 * owning {@link ProductCategory} is saved.
 *
 * <p><strong>Idempotency key is the category's name</strong> — a category that already has at
 * least one {@link ProductCategoryAttribute} assignment is skipped entirely (its whole schema is
 * assumed already seeded), the same "small, fixed sample dataset" reasoning
 * {@link ProductCategorySeeder}'s own Javadoc gives for keying off name rather than a decoupled
 * {@code seedId}.
 *
 * <p>Must run after both {@link ProductCategorySeeder} and {@link ProductAttributeSeeder} (whose
 * rows this seeder resolves by name), and before {@link ProductSeeder} for name-resolution
 * ordering reasons only — {@code ProductServiceImpl} never validates a variant's own
 * {@code attributes} against a category's schema (advisory only, see {@link
 * ProductCategoryAttribute}'s own Javadoc), so an inconsistency here would no longer fail loudly
 * inside {@link ProductSeeder} the way it once did. The sample catalog's own
 * {@code product_variants.csv} data is still kept consistent with whatever schema this seeder
 * assigns, purely so the admin GUI's suggested-attribute rows stay accurate for the sample
 * catalog — see {@code ecommerce-service/CLAUDE.md}'s own note on this feature for the full
 * per-category breakdown.
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCategoryAttributeSeeder implements Seeder {

    private static final String CSV_LOCATION = "data/csv/product_category_attributes.csv";

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductAttributeRepository productAttributeRepository;

    /**
     * {@code @Transactional} is required here — unlike {@code CsvSeeder}'s per-row
     * {@code repository.save()} calls (each its own short-lived transaction via
     * {@code SimpleJpaRepository}), this method fetches a {@link ProductCategory}, reads its lazy
     * {@code categoryAttributes} collection, and saves it back all in one unit of work; without a
     * surrounding transaction the Hibernate session from {@code findByNameIgnoreCase} closes the
     * moment that call returns, and the very next line's {@code category.getCategoryAttributes()}
     * throws {@code LazyInitializationException: no Session}.
     */
    @Override
    @Transactional
    public int seed() {
        int inserted = 0;
        int skipped = 0;
        for (Map.Entry<String, List<CSVRecord>> entry : groupByCategoryName().entrySet()) {
            String categoryName = entry.getKey();
            ProductCategory category = productCategoryRepository.findByNameIgnoreCase(categoryName)
                    .orElseThrow(() -> new IllegalStateException("product_category_attributes.csv references unknown "
                            + "category '" + categoryName + "' — seed product_categories.csv first"));

            if (!category.getCategoryAttributes().isEmpty()) {
                skipped += entry.getValue().size();
                log.debug("ProductCategoryAttributeSeeder: skipping already-assigned category '{}'", categoryName);
                continue;
            }

            List<CSVRecord> rows = entry.getValue();
            for (int i = 0; i < rows.size(); i++) {
                CSVRecord row = rows.get(i);
                String attributeName = row.get("attributeName");
                ProductAttribute attribute = productAttributeRepository.findByNameIgnoreCase(attributeName)
                        .orElseThrow(() -> new IllegalStateException("product_category_attributes.csv references "
                                + "unknown attribute '" + attributeName + "' — seed product_attributes.csv first"));

                ProductCategoryAttribute assignment = new ProductCategoryAttribute();
                assignment.setCategory(category);
                assignment.setAttribute(attribute);
                assignment.setRequired(Boolean.parseBoolean(row.get("required")));
                assignment.setDisplayOrder(i);
                category.getCategoryAttributes().add(assignment);
            }
            productCategoryRepository.save(category);
            inserted += rows.size();
        }

        log.info("ProductCategoryAttributeSeeder: inserted {} assignment(s), skipped {} already-present assignment(s)",
                inserted, skipped);
        return inserted;
    }

    private Map<String, List<CSVRecord>> groupByCategoryName() {
        Map<String, List<CSVRecord>> byCategory = new LinkedHashMap<>();
        for (CSVRecord record : readCsv(CSV_LOCATION)) {
            byCategory.computeIfAbsent(record.get("categoryName"), k -> new ArrayList<>()).add(record);
        }
        return byCategory;
    }

    /** Same read shape as {@code infra}'s {@code CsvSeeder} (and {@code ProductSeeder}'s own
     * identical duplicate of it), duplicated here rather than reused — that class's {@code seed()}
     * is {@code final} and owns the whole read-and-persist loop, which doesn't fit a seeder that
     * must gather every row for one category before persisting anything. */
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
