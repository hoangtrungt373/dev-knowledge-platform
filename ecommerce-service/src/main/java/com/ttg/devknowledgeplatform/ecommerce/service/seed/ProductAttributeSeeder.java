package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttributeValue;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductAttributeRepository;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;

import lombok.RequiredArgsConstructor;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Seeds {@link ProductAttribute} rows (plus their cascade-owned {@link ProductAttributeValue}
 * vocabulary) from {@code data/csv/product_attributes.csv} (columns: {@code name}, {@code values}
 * — the latter semicolon-joined, same compact inline convention {@code product_variants.csv}'s own
 * {@code attributes} cell uses for key/value pairs, just a flat list here since a {@code
 * ProductAttribute} has no keys of its own).
 *
 * <p>Must run before {@link ProductCategoryAttributeSeeder} (which resolves each row's
 * {@code attributeName} against rows this seeder already persisted) and before {@link
 * ProductSeeder} (whose variants get validated against whichever category schema
 * {@code ProductCategoryAttributeSeeder} assigns) — see {@link EcommerceDataSeedingRunner}'s own
 * ordering.
 *
 * <p>Bypasses {@code ProductAttributeService} and persists directly via the repository, mirroring
 * {@code ProductTagSeeder} rather than {@code ProductSeeder} — like a {@code ProductTag}, creating
 * a {@code ProductAttribute} has no outbox event / read-model side effect that would require going
 * through the service layer. {@code displayOrder} is each value's position in the CSV cell's own
 * semicolon-separated list, matching {@code ProductAttributeServiceImpl.applyValues}' identical
 * "list position, not a caller-supplied number" convention.
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class ProductAttributeSeeder extends CsvSeeder<ProductAttribute> {

    private final ProductAttributeRepository productAttributeRepository;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/product_attributes.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        return productAttributeRepository.existsByNameIgnoreCase(record.get("name"));
    }

    @Override
    protected ProductAttribute buildEntity(CSVRecord record) {
        ProductAttribute attribute = new ProductAttribute();
        attribute.setName(record.get("name"));

        String[] values = record.get("values").split(";");
        for (int i = 0; i < values.length; i++) {
            ProductAttributeValue value = new ProductAttributeValue();
            value.setAttribute(attribute);
            value.setValue(values[i].trim());
            value.setDisplayOrder(i);
            attribute.getValues().add(value);
        }
        return attribute;
    }

    @Override
    protected void persist(ProductAttribute entity) {
        // Cascades to ProductAttributeValue — see ProductAttribute.values' own Javadoc.
        productAttributeRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("name");
    }
}
