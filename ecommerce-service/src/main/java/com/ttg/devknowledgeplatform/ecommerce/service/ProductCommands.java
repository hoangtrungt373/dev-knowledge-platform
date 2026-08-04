package com.ttg.devknowledgeplatform.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Plain input records for {@link ProductService}, mirroring {@code api}'s
 * {@code CreateProductRequest}/{@code UpdateProductRequest} field-for-field but without any
 * REST/validation concerns — those stay in {@code api}, which does the request-DTO-to-command
 * translation before calling the service. Same pattern as {@code content-service}'s
 * {@code QuestionAnswerCommands}.
 */
public final class ProductCommands {

    private ProductCommands() {}

    public record VariantInput(String sku, BigDecimal price, Integer stockQuantity, Map<String, String> attributes) {
    }

    public record ImageInput(String storageKey, Integer sortOrder) {
    }

    public record Create(
            String name,
            String description,
            Integer productCategoryId,
            List<VariantInput> variants,
            List<ImageInput> images) {
    }

    /** Basic-field update only — variant/image mutation gets its own endpoints in a later slice. */
    public record Update(String name, String description, Integer productCategoryId) {
    }
}
