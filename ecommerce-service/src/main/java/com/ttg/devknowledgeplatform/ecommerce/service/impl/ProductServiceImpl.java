package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxAggregateType;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductImageRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.ProductSpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SlugService slugService;
    private final StorageService storageService;

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

        publishProductChanged(saved.getId());
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
        publishProductChanged(updated.getId());
        log.info("Updated product id={}", id);
        return updated;
    }

    @Override
    public Product deactivate(Integer id) {
        Product product = findById(id);
        product.setActive(false);
        Product deactivated = productRepository.save(product);
        publishProductChanged(deactivated.getId());
        log.info("Deactivated product id={}", id);
        return deactivated;
    }

    @Override
    public Product getById(Integer id) {
        return findById(id);
    }

    @Override
    public Product getActiveBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(EcommerceErrorCode.PRODUCT_NOT_FOUND, new Object[] {slug}));
    }

    @Override
    public Page<Product> list(Pageable pageable, Integer productCategoryId, Boolean active, String q) {
        Specification<Product> spec = ProductSpecification.withFilters(productCategoryId, active, q);
        return productRepository.findAll(spec, pageable);
    }

    @Override
    public ProductVariant addVariant(Integer productId, ProductCommands.VariantInput input) {
        Product product = findById(productId);
        if (productVariantRepository.existsBySku(input.sku())) {
            throw new ApiException(EcommerceErrorCode.PRODUCT_VARIANT_SKU_CONFLICT, new Object[] {input.sku()});
        }
        validateAttributeKeysMatchExisting(product, input.attributes());

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(input.sku());
        variant.setPrice(input.price());
        variant.setStockQuantity(input.stockQuantity());
        variant.setReservedQuantity(0);
        variant.setAttributes(input.attributes() == null ? Map.of() : input.attributes());
        ProductVariant saved = productVariantRepository.save(variant);
        product.getVariants().add(saved);

        publishProductChanged(productId);
        log.info("Added variant sku={} to product id={}", input.sku(), productId);
        return saved;
    }

    @Override
    public void removeVariant(Integer productId, Integer variantId) {
        Product product = findById(productId);
        ProductVariant variant = findVariantById(variantId);
        validateVariantBelongsToProduct(variant, product);

        if (product.getVariants().size() <= 1) {
            throw new ApiException(EcommerceErrorCode.PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT);
        }

        product.getVariants().remove(variant);
        productVariantRepository.delete(variant);

        publishProductChanged(productId);
        log.info("Removed variant id={} from product id={}", variantId, productId);
    }

    @Override
    public ProductImage addImage(Integer productId, ProductCommands.ImageInput input) {
        Product product = findById(productId);
        validateSortOrderAvailable(product, input.sortOrder(), null);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setStorageKey(input.storageKey());
        image.setSortOrder(input.sortOrder());
        ProductImage saved = productImageRepository.save(image);
        product.getImages().add(saved);

        publishProductChanged(productId);
        log.info("Added image sortOrder={} to product id={}", input.sortOrder(), productId);
        return saved;
    }

    @Override
    public ProductImage uploadImage(Integer productId, MultipartFile file, Integer sortOrder) {
        Product product = findById(productId);
        validateSortOrderAvailable(product, sortOrder, null);

        // Uploads the bytes first (outside any existing image's identity) so a validation failure
        // in StorageService (wrong content type, over 5 MB) never leaves a half-created
        // ProductImage row behind — matches addImage's ordering, just with a real upload in place
        // of trusting a client-supplied key.
        String objectKey = storageService.uploadImage("products/" + productId + "/" + UUID.randomUUID(), file);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setStorageKey(objectKey);
        image.setSortOrder(sortOrder);
        ProductImage saved = productImageRepository.save(image);
        product.getImages().add(saved);

        publishProductChanged(productId);
        log.info("Uploaded image sortOrder={} to product id={}", sortOrder, productId);
        return saved;
    }

    @Override
    public void removeImage(Integer productId, Integer imageId) {
        Product product = findById(productId);
        ProductImage image = findImageById(imageId);
        validateImageBelongsToProduct(image, product);

        product.getImages().remove(image);
        productImageRepository.delete(image);

        publishProductChanged(productId);
        log.info("Removed image id={} from product id={}", imageId, productId);
    }

    @Override
    public ProductImage updateImageSortOrder(Integer productId, Integer imageId, Integer newSortOrder) {
        Product product = findById(productId);
        ProductImage image = findImageById(imageId);
        validateImageBelongsToProduct(image, product);
        validateSortOrderAvailable(product, newSortOrder, imageId);

        image.setSortOrder(newSortOrder);
        ProductImage updated = productImageRepository.save(image);

        publishProductChanged(productId);
        log.info("Updated sort order of image id={} on product id={} to {}", imageId, productId, newSortOrder);
        return updated;
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

    private ProductVariant findVariantById(Integer id) {
        return productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(EcommerceErrorCode.PRODUCT_VARIANT_NOT_FOUND, new Object[] {id}));
    }

    private ProductImage findImageById(Integer id) {
        return productImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(EcommerceErrorCode.PRODUCT_IMAGE_NOT_FOUND, new Object[] {id}));
    }

    private static void validateVariantBelongsToProduct(ProductVariant variant, Product product) {
        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new ApiException(EcommerceErrorCode.PRODUCT_VARIANT_BELONGS_TO_ANOTHER_PRODUCT,
                    new Object[] {variant.getId(), product.getId()});
        }
    }

    private static void validateImageBelongsToProduct(ProductImage image, Product product) {
        if (!image.getProduct().getId().equals(product.getId())) {
            throw new ApiException(EcommerceErrorCode.PRODUCT_IMAGE_BELONGS_TO_ANOTHER_PRODUCT,
                    new Object[] {image.getId(), product.getId()});
        }
    }

    /**
     * A new/moved-to sort order must not collide with any of the product's *other* images.
     * {@code excludingImageId} is the image being reordered (excluded from the conflict check
     * against itself); {@code null} when adding a brand-new image.
     */
    private static void validateSortOrderAvailable(Product product, Integer sortOrder, Integer excludingImageId) {
        boolean taken = product.getImages().stream()
                .filter(image -> excludingImageId == null || !image.getId().equals(excludingImageId))
                .anyMatch(image -> image.getSortOrder().equals(sortOrder));
        if (taken) {
            throw new ApiException(EcommerceErrorCode.PRODUCT_IMAGE_SORT_ORDER_CONFLICT, new Object[] {sortOrder});
        }
    }

    /**
     * A newly-added variant must share the existing variants' attribute keys (US-1.6) — the
     * create-time check ({@code validateConsistentAttributeKeys}) only compares variants within
     * one request; this compares against what the product already has.
     */
    private static void validateAttributeKeysMatchExisting(Product product, Map<String, String> newAttributes) {
        if (product.getVariants().isEmpty()) {
            return;
        }
        Set<String> existingKeys = product.getVariants().get(0).getAttributes().keySet();
        Set<String> newKeys = newAttributes == null ? Set.of() : newAttributes.keySet();
        if (!existingKeys.equals(newKeys)) {
            throw new ApiException(EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_KEYS_INCONSISTENT);
        }
    }

    /**
     * Writes a {@code PRODUCT_CHANGED} outbox row in the same transaction as the triggering
     * change — the transactional-outbox guarantee (see {@code OutboxEvent}'s Javadoc). The
     * {@code OutboxRelay} picks it up and re-derives {@code ProductSearchView} from current
     * state; the payload only needs the id, never a diff.
     */
    private void publishProductChanged(Integer productId) {
        OutboxEvent event = new OutboxEvent();
        // References the handler's own constant/payload type rather than retyping the literal
        // eventType or the "productId" map key — see ProductChangedOutboxEventHandler's Javadoc.
        event.setEventType(ProductChangedOutboxEventHandler.EVENT_TYPE);
        event.setAggregateType(OutboxAggregateType.PRODUCT);
        event.setAggregateId(productId);
        event.setPayload(new ProductChangedOutboxEventHandler.Payload(productId).toMap());
        outboxEventRepository.save(event);
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
