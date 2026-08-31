package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;

import lombok.RequiredArgsConstructor;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Seeds {@link ProductCategory} rows from {@code data/csv/product_categories.csv} (columns:
 * {@code name}, {@code parentName}).
 *
 * <p><strong>Idempotency key — and parent reference — is {@code name} itself, not a decoupled
 * {@code seedId}</strong> — unlike {@code content-service}'s {@code CategorySeeder}/
 * {@code TagSeeder}, which persist a permanent {@code seedId} column precisely so a category's
 * display name stays freely editable across re-seeds. This starter catalog is a small, fixed
 * sample dataset, not long-lived production content coexisting with user edits, so that decoupling
 * wasn't judged worth a schema migration when hierarchy support was added here either — if a
 * category is ever renamed through the admin GUI, re-running this seeder would treat the CSV row
 * as a new, distinct category rather than recognizing it (and, if it had children, would leave
 * their own {@code parentName} references pointing at a name nothing resolves to anymore). Revisit
 * with a real {@code seedId} column (mirroring {@code content-service}'s pattern exactly) if this
 * seed data outgrows "fixed sample catalog."
 *
 * <p><strong>Rows must list parent categories before their children</strong> — {@link CsvSeeder}
 * persists each row in file order before moving to the next, so a child's {@code parentName} is
 * resolved by looking up an already-persisted category by name in the same pass (same ordering
 * requirement as {@code content-service}'s {@code CategorySeeder}, just keyed by name here instead
 * of {@code seedId}).
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class ProductCategorySeeder extends CsvSeeder<ProductCategory> {

    private final ProductCategoryRepository productCategoryRepository;
    private final SlugService slugService;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/product_categories.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        return productCategoryRepository.existsByNameIgnoreCase(record.get("name"));
    }

    @Override
    protected ProductCategory buildEntity(CSVRecord record) {
        String name = record.get("name");
        String parentName = record.get("parentName");

        ProductCategory parent = null;
        if (parentName != null && !parentName.isBlank()) {
            parent = productCategoryRepository.findByNameIgnoreCase(parentName)
                    .orElseThrow(() -> new IllegalStateException(
                            "product_categories.csv references unknown parentName '" + parentName
                                    + "' — parent rows must appear before their children"));
        }

        ProductCategory category = new ProductCategory();
        category.setName(name);
        category.setSlug(slugService.generateUniqueSlug(
                name, productCategoryRepository::existsBySlug, EcommerceErrorCode.PRODUCT_CATEGORY_SLUG_CONFLICT));
        category.setParent(parent);
        return category;
    }

    @Override
    protected void persist(ProductCategory entity) {
        productCategoryRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("name");
    }
}
