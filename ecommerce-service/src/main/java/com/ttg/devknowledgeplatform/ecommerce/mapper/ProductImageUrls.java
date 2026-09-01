package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.infra.service.StorageService;

import java.util.Comparator;

/**
 * Resolves a {@link Product}'s first gallery image (by {@code sortOrder}, since
 * {@code Product.images} carries no {@code @OrderBy} of its own) into a presigned URL — null if
 * the product has no images yet. Extracted once this exact logic had been copy-pasted verbatim
 * into both {@link CartMapper#toLineResponse}'s and {@link OrderMapper#toOrderLineResponse}'s own
 * private {@code resolvePrimaryImageUrl} methods (each one's Javadoc cross-referencing the
 * other's copy rather than sharing it) — both still resolve the same nullable shape
 * {@code ProductSearchViewMapper.resolvePrimaryImageUrl} uses for the storefront grid's own
 * thumbnail, just with a different starting entity.
 */
final class ProductImageUrls {

    private ProductImageUrls() {
    }

    static String resolvePrimaryImageUrl(Product product, StorageService storageService) {
        return product.getImages().stream()
                .min(Comparator.comparing(ProductImage::getSortOrder))
                .map(image -> storageService.getPresignedUrl(image.getStorageKey()))
                .orElse(null);
    }
}
