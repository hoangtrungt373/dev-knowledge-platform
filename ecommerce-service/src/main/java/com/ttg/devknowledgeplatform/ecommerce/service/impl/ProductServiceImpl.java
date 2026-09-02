package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategoryAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTagAssignment;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxAggregateType;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductImageRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductTagRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.ProductSpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductDescriptionSanitizer;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final ProductTagRepository productTagRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SlugService slugService;
    private final StorageService storageService;
    private final ProductDescriptionSanitizer productDescriptionSanitizer;

    @Override
    public Product create(ProductCommands.Create command) {
        List<ProductCommands.VariantInput> variants = command.variants();
        Validator.isFalse(variants == null || variants.isEmpty(), EcommerceErrorCode.PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT);
        validateNoDuplicateSkusInRequest(variants);
        validateConsistentAttributeKeys(variants);
        for (ProductCommands.VariantInput variant : variants) {
            Validator.isFalse(productVariantRepository.existsBySku(variant.sku()),
                    EcommerceErrorCode.PRODUCT_VARIANT_SKU_CONFLICT, variant.sku());
        }

        List<ProductCommands.ImageInput> images = command.images() == null ? List.of() : command.images();
        validateNoDuplicateSortOrdersInRequest(images);

        ProductCategory category = findCategoryById(command.productCategoryId());
        for (ProductCommands.VariantInput variant : variants) {
            validateAttributesAgainstCategory(category, variant.attributes());
        }
        String slug = slugService.generateUniqueSlug(
                command.name(), productRepository::existsBySlug, EcommerceErrorCode.PRODUCT_SLUG_CONFLICT);

        Product product = new Product();
        product.setName(command.name());
        product.setDescription(productDescriptionSanitizer.sanitize(command.description()));
        product.setSlug(slug);
        product.setActive(true);
        product.setProductCategory(category);
        Product saved = productRepository.save(product);

        // Always applied, even when the request omits tagIds entirely (null -> Set.of()) — unlike
        // update, a brand-new product has no prior tag state to "leave unchanged", so there's no
        // three-state semantics to preserve here (see applyTagIds' own Javadoc).
        applyTagIds(saved, command.tagIds() == null ? Set.of() : command.tagIds());

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
        product.setDescription(productDescriptionSanitizer.sanitize(command.description()));
        ProductCategory newCategory = findCategoryById(command.productCategoryId());
        // Re-validated against the (possibly new) category even though this method never touches
        // variants itself — moving a product into a category with its own attribute schema must
        // not silently leave existing variants violating it.
        for (ProductVariant variant : product.getVariants()) {
            validateAttributesAgainstCategory(newCategory, variant.getAttributes());
        }
        product.setProductCategory(newCategory);

        if (command.tagIds() != null) {
            applyTagIds(product, command.tagIds());
        }

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
        return Validator.notFound(
                productRepository.findBySlug(slug).filter(Product::isActive), EcommerceErrorCode.PRODUCT_NOT_FOUND, slug);
    }

    @Override
    public Page<Product> list(Pageable pageable, Integer productCategoryId, Boolean active, String q, Set<Integer> tagIds) {
        Specification<Product> spec = ProductSpecification.withFilters(productCategoryId, active, q, tagIds);
        return productRepository.findAll(spec, pageable);
    }

    @Override
    public ProductVariant addVariant(Integer productId, ProductCommands.VariantInput input) {
        Product product = findById(productId);
        Validator.isFalse(productVariantRepository.existsBySku(input.sku()), EcommerceErrorCode.PRODUCT_VARIANT_SKU_CONFLICT, input.sku());
        validateAttributeKeysMatchExisting(product, input.attributes(), null);
        validateAttributesAgainstCategory(product.getProductCategory(), input.attributes());

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
    public ProductVariant updateVariant(Integer productId, Integer variantId, ProductCommands.VariantInput input) {
        Product product = findById(productId);
        ProductVariant variant = findVariantById(variantId);
        validateVariantBelongsToProduct(variant, product);

        if (!variant.getSku().equals(input.sku())) {
            Validator.isFalse(productVariantRepository.existsBySkuAndIdNot(input.sku(), variantId),
                    EcommerceErrorCode.PRODUCT_VARIANT_SKU_CONFLICT, input.sku());
        }
        Validator.isTrue(input.stockQuantity() >= variant.getReservedQuantity(),
                EcommerceErrorCode.PRODUCT_VARIANT_STOCK_BELOW_RESERVED, input.stockQuantity(), variant.getReservedQuantity());
        // Compared against this product's *other* variants, not including the one being edited —
        // otherwise a variant being edited would always trivially match its own (about-to-change)
        // key set, defeating the check entirely.
        validateAttributeKeysMatchExisting(product, input.attributes(), variantId);
        validateAttributesAgainstCategory(product.getProductCategory(), input.attributes());

        variant.setSku(input.sku());
        variant.setPrice(input.price());
        variant.setStockQuantity(input.stockQuantity());
        variant.setAttributes(input.attributes() == null ? Map.of() : input.attributes());
        ProductVariant updated = productVariantRepository.save(variant);

        publishProductChanged(productId);
        log.info("Updated variant id={} on product id={}", variantId, productId);
        return updated;
    }

    @Override
    public void removeVariant(Integer productId, Integer variantId) {
        Product product = findById(productId);
        ProductVariant variant = findVariantById(variantId);
        validateVariantBelongsToProduct(variant, product);

        Validator.isFalse(product.getVariants().size() <= 1, EcommerceErrorCode.PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT);

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
        return Validator.notFound(productRepository.findById(id), EcommerceErrorCode.PRODUCT_NOT_FOUND, id);
    }

    private ProductCategory findCategoryById(Integer id) {
        return Validator.notFound(productCategoryRepository.findById(id), EcommerceErrorCode.PRODUCT_CATEGORY_NOT_FOUND, id);
    }

    private ProductVariant findVariantById(Integer id) {
        return Validator.notFound(productVariantRepository.findById(id), EcommerceErrorCode.PRODUCT_VARIANT_NOT_FOUND, id);
    }

    private ProductImage findImageById(Integer id) {
        return Validator.notFound(productImageRepository.findById(id), EcommerceErrorCode.PRODUCT_IMAGE_NOT_FOUND, id);
    }

    private static void validateVariantBelongsToProduct(ProductVariant variant, Product product) {
        Validator.isTrue(variant.getProduct().getId().equals(product.getId()),
                EcommerceErrorCode.PRODUCT_VARIANT_BELONGS_TO_ANOTHER_PRODUCT, variant.getId(), product.getId());
    }

    private static void validateImageBelongsToProduct(ProductImage image, Product product) {
        Validator.isTrue(image.getProduct().getId().equals(product.getId()),
                EcommerceErrorCode.PRODUCT_IMAGE_BELONGS_TO_ANOTHER_PRODUCT, image.getId(), product.getId());
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
        Validator.isFalse(taken, EcommerceErrorCode.PRODUCT_IMAGE_SORT_ORDER_CONFLICT, sortOrder);
    }

    /**
     * A newly-added or edited variant must share the product's other variants' attribute keys
     * (US-1.6) — the create-time check ({@code validateConsistentAttributeKeys}) only compares
     * variants within one request; this compares against what the product already has.
     *
     * @param excludingVariantId when editing an existing variant, that variant's own id — excluded
     *                           from the "reference" variant lookup so a variant being edited is
     *                           never compared against its own (about-to-change) key set, which
     *                           would trivially match and defeat the check entirely. {@code null}
     *                           when adding a brand-new variant (nothing to exclude).
     */
    private static void validateAttributeKeysMatchExisting(Product product, Map<String, String> newAttributes, Integer excludingVariantId) {
        Optional<ProductVariant> reference = product.getVariants().stream()
                .filter(v -> excludingVariantId == null || !v.getId().equals(excludingVariantId))
                .findFirst();
        if (reference.isEmpty()) {
            return;
        }
        Set<String> existingKeys = reference.get().getAttributes().keySet();
        Set<String> newKeys = newAttributes == null ? Set.of() : newAttributes.keySet();
        Validator.isTrue(existingKeys.equals(newKeys), EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_KEYS_INCONSISTENT);
    }

    /**
     * Enforces {@code category}'s own attribute schema (the "Option B" global {@link
     * ProductAttribute} registry, assigned via {@link ProductCategoryAttribute}) against one
     * variant's {@code attributes} map — a category with zero assignments is unconstrained
     * (today's free-form behavior, unchanged); enforcement only turns on once at least one
     * assignment exists. Every key present must be one of the category's assigned attribute
     * names; every {@code required}-flagged name must be present; and each present value must be
     * one of that attribute's own defined {@link ProductAttributeValue}s.
     *
     * <p>Composes with, rather than replaces, {@link #validateConsistentAttributeKeys}/
     * {@link #validateAttributeKeysMatchExisting} — those two enforce "every variant of this
     * product shares one key set"; this enforces "that shared key set (and each variant's own
     * values) is actually valid for the product's category."
     */
    private static void validateAttributesAgainstCategory(ProductCategory category, Map<String, String> attributes) {
        List<ProductCategoryAttribute> definitions = category.getCategoryAttributes();
        if (definitions.isEmpty()) {
            return;
        }
        Map<String, String> actual = attributes == null ? Map.of() : attributes;

        for (String key : actual.keySet()) {
            boolean allowed = definitions.stream().anyMatch(d -> d.getAttribute().getName().equals(key));
            Validator.isTrue(allowed, EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_NOT_ALLOWED_FOR_CATEGORY, key);
        }
        for (ProductCategoryAttribute definition : definitions) {
            String name = definition.getAttribute().getName();
            if (definition.isRequired()) {
                Validator.isTrue(actual.containsKey(name), EcommerceErrorCode.PRODUCT_VARIANT_REQUIRED_ATTRIBUTE_MISSING, name);
            }
            String value = actual.get(name);
            if (value == null) {
                continue;
            }
            boolean allowedValue = definition.getAttribute().getValues().stream()
                    .anyMatch(v -> v.getValue().equals(value));
            Validator.isTrue(allowedValue, EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_VALUE_NOT_ALLOWED, value, name);
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
            Validator.isTrue(seen.add(variant.sku()),
                    EcommerceErrorCode.PRODUCT_VARIANT_DUPLICATE_SKU_IN_REQUEST, variant.sku());
        }
    }

    private static void validateNoDuplicateSortOrdersInRequest(List<ProductCommands.ImageInput> images) {
        Set<Integer> seen = new HashSet<>();
        for (ProductCommands.ImageInput image : images) {
            Validator.isTrue(seen.add(image.sortOrder()),
                    EcommerceErrorCode.PRODUCT_IMAGE_DUPLICATE_SORT_ORDER, image.sortOrder());
        }
    }

    /**
     * Clears and rebuilds {@code product.productTagAssignments} from {@code tagIds} — mirrors
     * {@code content-service}'s {@code QuestionAnswerServiceImpl.applyTagIds} exactly, minus the
     * "reject inactive tags" step that class has, since {@link ProductTag} has no status/lifecycle
     * field at all (a deliberate simplification — see that entity's own Javadoc).
     *
     * <p>Called from {@code create} unconditionally (an empty/{@code null} set just means "no
     * tags") and from {@code update} only when {@code command.tagIds() != null} — see each call
     * site's own comment for the three-state semantics {@code update} preserves.
     */
    private void applyTagIds(Product product, Set<Integer> tagIds) {
        Validator.isFalse(tagIds.stream().anyMatch(Objects::isNull),
                CommonErrorCode.VALIDATION_FIELD_INVALID, "tagIds must not contain null");
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(tagIds);
        if (unique.isEmpty()) {
            product.getProductTagAssignments().clear();
            return;
        }

        List<ProductTag> existing = productTagRepository.findAllById(unique);
        Validator.isTrue(existing.size() == unique.size(),
                EcommerceErrorCode.PRODUCT_TAG_NOT_FOUND, "One or more product tags were not found");

        product.getProductTagAssignments().clear();
        for (Integer tagId : unique) {
            ProductTagAssignment assignment = new ProductTagAssignment();
            assignment.setProduct(product);
            assignment.setProductTag(productTagRepository.getReferenceById(tagId));
            product.getProductTagAssignments().add(assignment);
        }
    }

    /** All variants of one product must share the same attribute keys — see US-1.6. */
    private static void validateConsistentAttributeKeys(List<ProductCommands.VariantInput> variants) {
        Set<String> firstKeys = null;
        for (ProductCommands.VariantInput variant : variants) {
            Set<String> keys = variant.attributes() == null ? Set.of() : new HashSet<>(variant.attributes().keySet());
            if (firstKeys == null) {
                firstKeys = keys;
            } else {
                Validator.isTrue(firstKeys.equals(keys), EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_KEYS_INCONSISTENT);
            }
        }
    }
}
