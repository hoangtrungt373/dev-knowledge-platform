package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductTagRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;

import lombok.RequiredArgsConstructor;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Seeds {@link ProductTag} rows from {@code data/csv/product_tags.csv} (single column:
 * {@code name}).
 *
 * <p><strong>Idempotency key is {@code name} itself, not a decoupled {@code seedId}</strong> —
 * same simplification {@link ProductCategorySeeder} makes for the same reason (see its own
 * Javadoc): this is a small, fixed starter catalog, not long-lived production content, and
 * {@link ProductTag} itself was deliberately kept simple (no status/lifecycle field — see its own
 * Javadoc) for the identical reason.
 *
 * <p>Bypasses {@code ProductTagService} and persists directly via the repository, mirroring
 * {@code ProductCategorySeeder} rather than {@code ProductSeeder} — unlike a {@code Product},
 * creating a {@code ProductTag} has no outbox event / read-model side effect that would require
 * going through the service layer.
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class ProductTagSeeder extends CsvSeeder<ProductTag> {

    private final ProductTagRepository productTagRepository;
    private final SlugService slugService;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/product_tags.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        return productTagRepository.existsByNameIgnoreCase(record.get("name"));
    }

    @Override
    protected ProductTag buildEntity(CSVRecord record) {
        String name = record.get("name");

        ProductTag tag = new ProductTag();
        tag.setName(name);
        tag.setSlug(slugService.generateUniqueSlug(
                name, productTagRepository::existsBySlug, EcommerceErrorCode.PRODUCT_TAG_SLUG_CONFLICT));
        return tag;
    }

    @Override
    protected void persist(ProductTag entity) {
        productTagRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("name");
    }
}
