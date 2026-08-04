package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductImageRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.ProductSpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final SlugService slugService;

    @Override
    public Product create(ProductCommands.Create command) {
        List<ProductCommands.VariantInput> variants = command.variants();
        if (variants == null || variants.isEmpty()) {
            throw new ApiException(EcommerceErrorCode.PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT);
        }
        validateNoDuplicateSkusInRequest(variants);
        validateConsistentAttributeKeys(variants);
        for (ProductCommands.VariantInput variant : variants) {
            if (productVariantRepository.existsBySku(variant.sku())) {
                throw new ApiException(EcommerceErrorCode.PRODUCT_VARIANT_SKU_CONFLICT, new Object[] {variant.sku()});
            }
        }

        List<ProductCommands.ImageInput> images = command.images() == null ? List.of() : command.images();
        validateNoDuplicateSortOrdersInRequest(images);

        ProductCategory category = findCategoryById(command.productCategoryId());
        String slug = slugService.generateUniqueSlug(
                command.name(), productRepository::existsBySlug, EcommerceErrorCode.PRODUCT_SLUG_CONFLICT);

        Product product = new Product();
        product.setName(command.name());
        product.setDescription(command.description());
        product.setSlug(slug);
        product.setActive(true);
        product.setProductCategory(category);
        Product saved = productRepository.save(product);

        for (ProductCommands.VariantInput variantInput : variants) {
            ProductVariant variant = new ProductVariant();
            variant.setProduct(saved);
            variant.setSku(variantInput.sku());
            variant.setPrice(variantInput.price());
            variant.setStockQuantity(variantInput.stockQuantity());
            variant.setReservedQuantity(0);
            variant.setAttributes(variantInput.attributes() == null ? Map.of() : variantInput.attributes());
            // Added to saved's own collection (not just persisted via its own repository) so the
            // in-memory graph is immediately consistent — a lazy re-fetch of `variants` right
            // after persisting a brand-new parent can't be relied on to see siblings created in
            // the same transaction (Hibernate may already treat a new entity's collection as
            // "initialized empty" at persist time, before these inserts existed).
            saved.getVariants().add(productVariantRepository.save(variant));
        }

        for (ProductCommands.ImageInput imageInput : images) {
            ProductImage image = new ProductImage();
            image.setProduct(saved);
            image.setStorageKey(imageInput.storageKey());
            image.setSortOrder(imageInput.sortOrder());
            saved.getImages().add(productImageRepository.save(image));
        }

        log.info("Created product id={} slug={} variantCount={} imageCount={}",
                saved.getId(), slug, variants.size(), images.size());
        return saved;
    }

    @Override
    public Product update(Integer id, ProductCommands.Update command) {
        Product product = findById(id);

        if (!product.getName().equalsIgnoreCase(command.name())) {
            product.setName(command.name());
            product.setSlug(slugService.generateUniqueSlug(
                    command.name(), productRepository::existsBySlugAndIdNot, id, EcommerceErrorCode.PRODUCT_SLUG_CONFLICT));
        }
        product.setDescription(command.description());
        product.setProductCategory(findCategoryById(command.productCategoryId()));

        Product updated = productRepository.save(product);
        log.info("Updated product id={}", id);
        return updated;
    }

    @Override
    public Product deactivate(Integer id) {
        Product product = findById(id);
        product.setActive(false);
        Product deactivated = productRepository.save(product);
        log.info("Deactivated product id={}", id);
        return deactivated;
    }

    @Override
    public Product getById(Integer id) {
        return findById(id);
    }

    @Override
    public Page<Product> list(Pageable pageable, Integer productCategoryId, Boolean active, String q) {
        Specification<Product> spec = ProductSpecification.withFilters(productCategoryId, active, q);
        return productRepository.findAll(spec, pageable);
    }

    private Product findById(Integer id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(EcommerceErrorCode.PRODUCT_NOT_FOUND, new Object[] {id}));
    }

    private ProductCategory findCategoryById(Integer id) {
        return productCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EcommerceErrorCode.PRODUCT_CATEGORY_NOT_FOUND, new Object[] {id}));
    }

    private static void validateNoDuplicateSkusInRequest(List<ProductCommands.VariantInput> variants) {
        Set<String> seen = new HashSet<>();
        for (ProductCommands.VariantInput variant : variants) {
            if (!seen.add(variant.sku())) {
                throw new ApiException(
                        EcommerceErrorCode.PRODUCT_VARIANT_DUPLICATE_SKU_IN_REQUEST, new Object[] {variant.sku()});
            }
        }
    }

    private static void validateNoDuplicateSortOrdersInRequest(List<ProductCommands.ImageInput> images) {
        Set<Integer> seen = new HashSet<>();
        for (ProductCommands.ImageInput image : images) {
            if (!seen.add(image.sortOrder())) {
                throw new ApiException(
                        EcommerceErrorCode.PRODUCT_IMAGE_DUPLICATE_SORT_ORDER, new Object[] {image.sortOrder()});
            }
        }
    }

    /** All variants of one product must share the same attribute keys — see US-1.6. */
    private static void validateConsistentAttributeKeys(List<ProductCommands.VariantInput> variants) {
        Set<String> firstKeys = null;
        for (ProductCommands.VariantInput variant : variants) {
            Set<String> keys = variant.attributes() == null ? Set.of() : new HashSet<>(variant.attributes().keySet());
            if (firstKeys == null) {
                firstKeys = keys;
            } else if (!firstKeys.equals(keys)) {
                throw new ApiException(EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_KEYS_INCONSISTENT);
            }
        }
    }
}
