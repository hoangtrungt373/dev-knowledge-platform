package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductImageRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductService;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;

import lombok.RequiredArgsConstructor;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.awt.Color;

/**
 * Seeds a first gallery image for a handful of featured products, from
 * {@code data/csv/product_images.csv} (columns: {@code productName}, {@code color} — a hex string
 * for {@link PlaceholderImageGenerator}, {@code sortOrder}).
 *
 * <p>Runs after {@link ProductSeeder} (see {@link EcommerceDataSeedingRunner}'s ordering) — every
 * row references a product that must already exist. Routes through
 * {@link ProductService#uploadImage}, the same real upload path the admin GUI uses (see
 * {@code gui/CLAUDE.md}'s {@code @ecommerce} note), rather than inserting a {@code ProductImage}
 * row directly — this is deliberately the first real exercise of that endpoint, generating a
 * placeholder JPEG via {@link PlaceholderImageGenerator} and wrapping it in an
 * {@link InMemoryMultipartFile} instead of reading a file from disk, since no real product
 * photography exists for this sample catalog. Going through the service (not a bare repository
 * insert) also means it publishes {@code PRODUCT_CHANGED}, so the newly-uploaded image's storage
 * key becomes {@code ProductSearchView.primaryImageStorageKey} without a separate step.
 *
 * <p>{@code alreadyExists} checks for *any* existing image at the target {@code sortOrder} on
 * that product, not a permanent seed identifier — good enough for this fixed sample dataset (see
 * {@link ProductCategorySeeder}'s Javadoc for the same tradeoff applied to categories).
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class ProductImageSeeder extends CsvSeeder<Void> {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductService productService;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/product_images.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        Product product = findProduct(record);
        int sortOrder = Integer.parseInt(record.get("sortOrder"));
        return productImageRepository.findByProductId(product.getId()).stream()
                .anyMatch(image -> image.getSortOrder() == sortOrder);
    }

    @Override
    protected Void buildEntity(CSVRecord record) {
        Product product = findProduct(record);
        int sortOrder = Integer.parseInt(record.get("sortOrder"));
        Color background = Color.decode("#" + record.get("color"));
        byte[] jpegBytes = PlaceholderImageGenerator.generate(product.getName(), background);

        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file", product.getSlug() + "-placeholder.jpg", "image/jpeg", jpegBytes);
        productService.uploadImage(product.getId(), file, sortOrder);
        return null;
    }

    @Override
    protected void persist(Void entity) {
        // No-op — buildEntity already persisted the ProductImage via ProductService.uploadImage
        // (see class Javadoc for why this goes through the service instead of a bare repository
        // save).
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("productName") + "#" + record.get("sortOrder");
    }

    private Product findProduct(CSVRecord record) {
        String productName = record.get("productName");
        return productRepository.findByNameIgnoreCase(productName)
                .orElseThrow(() -> new IllegalStateException("product_images.csv references unknown product '"
                        + productName + "' — seed products.csv first"));
    }
}
