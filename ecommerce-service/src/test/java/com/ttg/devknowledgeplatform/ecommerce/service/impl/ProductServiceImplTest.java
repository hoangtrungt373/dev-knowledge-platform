package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttributeValue;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategoryAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTagAssignment;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductImageRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductTagRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductDescriptionSanitizer;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductServiceImpl} — the core of Epic 1's US-1.6 (create/update with
 * variants+gallery, independent variant/image mutation) and US-1.7 (deactivate). Uses Spring
 * Test's {@code MockMultipartFile} (test-scoped only, unlike the main-source
 * {@code service.seed.InMemoryMultipartFile} the sample-catalog seeder needs) for the upload
 * endpoint tests.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private ProductCategoryRepository productCategoryRepository;
    @Mock
    private ProductTagRepository productTagRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private SlugService slugService;
    @Mock
    private StorageService storageService;
    // @Spy, not @Mock — the real sanitizer runs (no stubbing needed for every test), so assertions
    // below see genuinely sanitized output rather than a mocked passthrough.
    @Spy
    private ProductDescriptionSanitizer productDescriptionSanitizer = new ProductDescriptionSanitizer();

    @InjectMocks
    private ProductServiceImpl service;

    private ProductCategory category;

    @BeforeEach
    void setUp() {
        category = new ProductCategory();
        category.setId(10);
        category.setName("Apparel");
        category.setSlug("apparel");
    }

    private static Product productWithId(Integer id) {
        Product product = new Product();
        product.setId(id);
        product.setName("404 Not Found T-Shirt");
        product.setSlug("404-not-found-t-shirt");
        product.setActive(true);
        // A bare, attribute-schema-free category — every real Product always has one
        // (PRODUCT_CATEGORY_ID is NOT NULL), and ProductServiceImpl.addVariant now reads it via
        // validateAttributesAgainstCategory; an empty categoryAttributes list keeps every test
        // built on this helper free-form (today's behavior) unless a test opts into a schema via
        // addCategoryAttribute(...) on this product's own category.
        product.setProductCategory(new ProductCategory());
        return product;
    }

    private static ProductVariant variant(Product product, Integer id, String sku, Map<String, String> attributes) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setPrice(BigDecimal.valueOf(24.99));
        variant.setStockQuantity(10);
        variant.setReservedQuantity(0);
        variant.setAttributes(attributes);
        return variant;
    }

    private static ProductImage image(Product product, Integer id, Integer sortOrder) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setProduct(product);
        image.setStorageKey("products/1/" + id + ".jpg");
        image.setSortOrder(sortOrder);
        return image;
    }

    /** Attaches a {@code ProductCategoryAttribute} assignment directly to {@code category}'s own
     * collection — the "Option B" global attribute registry (see {@code ProductAttribute}'s own
     * Javadoc) — for pinning down {@code ProductServiceImpl.validateAttributesAgainstCategory}'s
     * enforcement. Once at least one such assignment exists, that category is no longer free-form. */
    private static void addCategoryAttribute(ProductCategory category, String name, boolean required, String... values) {
        ProductAttribute attribute = new ProductAttribute();
        attribute.setName(name);
        for (String value : values) {
            ProductAttributeValue attributeValue = new ProductAttributeValue();
            attributeValue.setAttribute(attribute);
            attributeValue.setValue(value);
            attribute.getValues().add(attributeValue);
        }
        ProductCategoryAttribute assignment = new ProductCategoryAttribute();
        assignment.setCategory(category);
        assignment.setAttribute(attribute);
        assignment.setRequired(required);
        assignment.setDisplayOrder(category.getCategoryAttributes().size());
        category.getCategoryAttributes().add(assignment);
    }

    @Nested
    class Create {

        @Test
        void createsProductWithVariantsAndImagesAndPublishesOutboxEvent() {
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput(
                    "TEE-S-BLK", BigDecimal.valueOf(24.99), 40, Map.of("size", "S", "color", "Black"));
            ProductCommands.ImageInput imageInput = new ProductCommands.ImageInput("products/1/0.jpg", 0);
            ProductCommands.Create command = new ProductCommands.Create(
                    "404 Not Found T-Shirt", "A tee for empty search results", 10,
                    List.of(variantInput), List.of(imageInput), Set.of());

            when(productVariantRepository.existsBySku("TEE-S-BLK")).thenReturn(false);
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("404-not-found-t-shirt");
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                product.setId(1);
                return product;
            });
            when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
                ProductVariant variant = invocation.getArgument(0);
                variant.setId(100);
                return variant;
            });
            when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> {
                ProductImage image = invocation.getArgument(0);
                image.setId(200);
                return image;
            });

            Product result = service.create(command);

            assertThat(result.getId()).isEqualTo(1);
            assertThat(result.getSlug()).isEqualTo("404-not-found-t-shirt");
            assertThat(result.isActive()).isTrue();
            assertThat(result.getVariants()).hasSize(1);
            assertThat(result.getVariants().get(0).getSku()).isEqualTo("TEE-S-BLK");
            assertThat(result.getImages()).hasSize(1);
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        void sanitizesDescriptionHtmlBeforePersisting() {
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of());
            ProductCommands.Create command = new ProductCommands.Create(
                    "Tee", "<p>Nice</p><script>alert('xss')</script>", 10, List.of(variantInput), List.of(), Set.of());

            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("tee");
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                product.setId(1);
                return product;
            });
            when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Product result = service.create(command);

            assertThat(result.getDescription()).isEqualTo("<p>Nice</p>");
            verify(productDescriptionSanitizer).sanitize("<p>Nice</p><script>alert('xss')</script>");
        }

        @Test
        void rejectsCreateWithNoVariants() {
            ProductCommands.Create command = new ProductCommands.Create(
                    "No Variants", null, 10, List.of(), List.of(), Set.of());

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT);

            verify(productRepository, never()).save(any());
        }

        @Test
        void rejectsDuplicateSkuWithinTheSameRequest() {
            ProductCommands.VariantInput v1 = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of());
            ProductCommands.VariantInput v2 = new ProductCommands.VariantInput("SKU-1", BigDecimal.ONE, 5, Map.of());
            ProductCommands.Create command = new ProductCommands.Create("Dup SKU", null, 10, List.of(v1, v2), List.of(), Set.of());

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_DUPLICATE_SKU_IN_REQUEST);
        }

        @Test
        void rejectsSkuThatAlreadyExists() {
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of());
            ProductCommands.Create command = new ProductCommands.Create("Existing SKU", null, 10, List.of(variantInput), List.of(), Set.of());
            when(productVariantRepository.existsBySku("SKU-1")).thenReturn(true);

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_SKU_CONFLICT);
        }

        @Test
        void rejectsInconsistentAttributeKeysAcrossVariants() {
            ProductCommands.VariantInput v1 = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of("size", "S"));
            ProductCommands.VariantInput v2 = new ProductCommands.VariantInput("SKU-2", BigDecimal.TEN, 5, Map.of("color", "Black"));
            ProductCommands.Create command = new ProductCommands.Create("Mismatched", null, 10, List.of(v1, v2), List.of(), Set.of());

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_KEYS_INCONSISTENT);
        }

        @Test
        void rejectsVariantAttributeNotInCategorySchema() {
            addCategoryAttribute(category, "size", false, "S", "M", "L");
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput(
                    "SKU-1", BigDecimal.TEN, 5, Map.of("color", "Black"));
            ProductCommands.Create command = new ProductCommands.Create("Tee", null, 10, List.of(variantInput), List.of(), Set.of());
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_NOT_ALLOWED_FOR_CATEGORY);
        }

        @Test
        void rejectsMissingRequiredCategoryAttribute() {
            addCategoryAttribute(category, "size", true, "S", "M", "L");
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of());
            ProductCommands.Create command = new ProductCommands.Create("Tee", null, 10, List.of(variantInput), List.of(), Set.of());
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_REQUIRED_ATTRIBUTE_MISSING);
        }

        @Test
        void rejectsAttributeValueNotInTheAllowedList() {
            addCategoryAttribute(category, "color", true, "Red", "Blue");
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput(
                    "SKU-1", BigDecimal.TEN, 5, Map.of("color", "Green"));
            ProductCommands.Create command = new ProductCommands.Create("Tee", null, 10, List.of(variantInput), List.of(), Set.of());
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_VALUE_NOT_ALLOWED);
        }

        @Test
        void succeedsWhenAttributesSatisfyTheCategorySchema() {
            addCategoryAttribute(category, "color", true, "Red", "Blue");
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput(
                    "SKU-1", BigDecimal.TEN, 5, Map.of("color", "Red"));
            ProductCommands.Create command = new ProductCommands.Create("Tee", null, 10, List.of(variantInput), List.of(), Set.of());
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("tee");
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                product.setId(1);
                return product;
            });
            when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Product result = service.create(command);

            assertThat(result.getVariants()).hasSize(1);
        }

        @Test
        void rejectsDuplicateImageSortOrderWithinTheSameRequest() {
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of());
            ProductCommands.ImageInput i1 = new ProductCommands.ImageInput("a.jpg", 0);
            ProductCommands.ImageInput i2 = new ProductCommands.ImageInput("b.jpg", 0);
            ProductCommands.Create command = new ProductCommands.Create(
                    "Dup Sort", null, 10, List.of(variantInput), List.of(i1, i2), Set.of());
            when(productVariantRepository.existsBySku("SKU-1")).thenReturn(false);

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_IMAGE_DUPLICATE_SORT_ORDER);
        }

        @Test
        void rejectsUnknownCategory() {
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of());
            ProductCommands.Create command = new ProductCommands.Create("Orphan", null, 99, List.of(variantInput), List.of(), Set.of());
            when(productVariantRepository.existsBySku("SKU-1")).thenReturn(false);
            when(productCategoryRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void createsProductWithTagAssignments() {
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of());
            ProductCommands.Create command = new ProductCommands.Create(
                    "Tee", null, 10, List.of(variantInput), List.of(), Set.of(5, 6));

            ProductTag tag5 = new ProductTag();
            tag5.setId(5);
            tag5.setName("New Arrival");
            ProductTag tag6 = new ProductTag();
            tag6.setId(6);
            tag6.setName("Best Seller");

            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("tee");
            when(productTagRepository.findAllById(Set.of(5, 6))).thenReturn(List.of(tag5, tag6));
            when(productTagRepository.getReferenceById(5)).thenReturn(tag5);
            when(productTagRepository.getReferenceById(6)).thenReturn(tag6);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                product.setId(1);
                return product;
            });
            when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Product result = service.create(command);

            assertThat(result.getProductTagAssignments()).hasSize(2);
            assertThat(result.getProductTagAssignments().stream().map(a -> a.getProductTag().getId()))
                    .containsExactlyInAnyOrder(5, 6);
        }

        @Test
        void rejectsUnknownTagId() {
            ProductCommands.VariantInput variantInput = new ProductCommands.VariantInput("SKU-1", BigDecimal.TEN, 5, Map.of());
            ProductCommands.Create command = new ProductCommands.Create(
                    "Tee", null, 10, List.of(variantInput), List.of(), Set.of(5, 99));

            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("tee");
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(productTagRepository.findAllById(Set.of(5, 99))).thenReturn(List.of(new ProductTag())); // only 1 of 2 found

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_TAG_NOT_FOUND);
        }
    }

    @Nested
    class Update {

        @Test
        void updatesBasicFieldsWithoutRegeneratingSlugWhenNameUnchanged() {
            Product existing = productWithId(1);
            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCommands.Update command = new ProductCommands.Update("404 Not Found T-Shirt", "New description", 10, null);
            Product result = service.update(1, command);

            assertThat(result.getDescription()).isEqualTo("New description");
            assertThat(result.getSlug()).isEqualTo("404-not-found-t-shirt");
            verify(slugService, never()).generateUniqueSlug(anyString(), any(), any(), any());
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        void sanitizesDescriptionHtmlBeforePersisting() {
            Product existing = productWithId(1);
            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCommands.Update command = new ProductCommands.Update(
                    "404 Not Found T-Shirt", "<p onclick=\"alert('xss')\">Updated</p>", 10, null);
            Product result = service.update(1, command);

            assertThat(result.getDescription()).isEqualTo("<p>Updated</p>");
            verify(productDescriptionSanitizer).sanitize("<p onclick=\"alert('xss')\">Updated</p>");
        }

        @Test
        void regeneratesSlugWhenNameChanges() {
            Product existing = productWithId(1);
            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(slugService.generateUniqueSlug(eq("Renamed Tee"), any(), eq(1), eq(EcommerceErrorCode.PRODUCT_SLUG_CONFLICT)))
                    .thenReturn("renamed-tee");
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCommands.Update command = new ProductCommands.Update("Renamed Tee", null, 10, null);
            Product result = service.update(1, command);

            assertThat(result.getSlug()).isEqualTo("renamed-tee");
        }

        @Test
        void throwsWhenProductDoesNotExist() {
            when(productRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99, new ProductCommands.Update("X", null, 10, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void replacesTagAssignmentsWhenTagIdsProvided() {
            Product existing = productWithId(1);
            ProductTag oldTag = new ProductTag();
            oldTag.setId(1);
            ProductTagAssignment oldAssignment = new ProductTagAssignment();
            oldAssignment.setProduct(existing);
            oldAssignment.setProductTag(oldTag);
            existing.getProductTagAssignments().add(oldAssignment);

            ProductTag newTag = new ProductTag();
            newTag.setId(7);
            newTag.setName("Clearance");

            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(productTagRepository.findAllById(Set.of(7))).thenReturn(List.of(newTag));
            when(productTagRepository.getReferenceById(7)).thenReturn(newTag);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCommands.Update command = new ProductCommands.Update("404 Not Found T-Shirt", null, 10, Set.of(7));
            Product result = service.update(1, command);

            assertThat(result.getProductTagAssignments()).hasSize(1);
            assertThat(result.getProductTagAssignments().get(0).getProductTag().getId()).isEqualTo(7);
        }

        @Test
        void leavesTagAssignmentsUnchangedWhenTagIdsIsNull() {
            Product existing = productWithId(1);
            ProductTag existingTag = new ProductTag();
            existingTag.setId(1);
            ProductTagAssignment assignment = new ProductTagAssignment();
            assignment.setProduct(existing);
            assignment.setProductTag(existingTag);
            existing.getProductTagAssignments().add(assignment);

            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.findById(10)).thenReturn(Optional.of(category));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCommands.Update command = new ProductCommands.Update("404 Not Found T-Shirt", null, 10, null);
            Product result = service.update(1, command);

            assertThat(result.getProductTagAssignments()).containsExactly(assignment);
            verify(productTagRepository, never()).findAllById(any());
        }

        @Test
        void rejectsReassignmentToACategoryTheExistingVariantsDontSatisfy() {
            Product existing = productWithId(1);
            existing.getVariants().add(variant(existing, 100, "TEE-S-BLK", Map.of()));
            ProductCategory stricter = new ProductCategory();
            stricter.setId(20);
            addCategoryAttribute(stricter, "size", true, "S", "M", "L");

            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.findById(20)).thenReturn(Optional.of(stricter));

            ProductCommands.Update command = new ProductCommands.Update("404 Not Found T-Shirt", null, 20, null);

            assertThatThrownBy(() -> service.update(1, command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_REQUIRED_ATTRIBUTE_MISSING);

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    class Deactivate {

        @Test
        void setsInactiveAndPublishesOutboxEvent() {
            Product existing = productWithId(1);
            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Product result = service.deactivate(1);

            assertThat(result.isActive()).isFalse();
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        void throwsWhenProductDoesNotExist() {
            when(productRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deactivate(99)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetActiveBySlug {

        @Test
        void returnsProductWhenActive() {
            Product product = productWithId(1);
            when(productRepository.findBySlug("404-not-found-t-shirt")).thenReturn(Optional.of(product));

            assertThat(service.getActiveBySlug("404-not-found-t-shirt")).isEqualTo(product);
        }

        @Test
        void treatsInactiveProductAsNotFound() {
            Product product = productWithId(1);
            product.setActive(false);
            when(productRepository.findBySlug("404-not-found-t-shirt")).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> service.getActiveBySlug("404-not-found-t-shirt"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void treatsNonexistentSlugAsNotFound() {
            when(productRepository.findBySlug("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getActiveBySlug("ghost"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class AddVariant {

        @Test
        void addsVariantWhenSkuIsAvailableAndAttributesMatch() {
            Product product = productWithId(1);
            product.getVariants().add(variant(product, 100, "TEE-S-BLK", Map.of("size", "S")));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.existsBySku("TEE-M-BLK")).thenReturn(false);
            when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
                ProductVariant v = invocation.getArgument(0);
                v.setId(101);
                return v;
            });

            ProductCommands.VariantInput input = new ProductCommands.VariantInput(
                    "TEE-M-BLK", BigDecimal.valueOf(24.99), 20, Map.of("size", "M"));
            ProductVariant result = service.addVariant(1, input);

            assertThat(result.getId()).isEqualTo(101);
            assertThat(product.getVariants()).hasSize(2);
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        void rejectsSkuConflict() {
            Product product = productWithId(1);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.existsBySku("TEE-M-BLK")).thenReturn(true);

            ProductCommands.VariantInput input = new ProductCommands.VariantInput(
                    "TEE-M-BLK", BigDecimal.valueOf(24.99), 20, Map.of());

            assertThatThrownBy(() -> service.addVariant(1, input))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_SKU_CONFLICT);
        }

        @Test
        void rejectsAttributeKeysThatDontMatchExistingVariants() {
            Product product = productWithId(1);
            product.getVariants().add(variant(product, 100, "TEE-S-BLK", Map.of("size", "S")));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.existsBySku("TEE-M")).thenReturn(false);

            ProductCommands.VariantInput input = new ProductCommands.VariantInput(
                    "TEE-M", BigDecimal.valueOf(24.99), 20, Map.of("color", "Black"));

            assertThatThrownBy(() -> service.addVariant(1, input))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_KEYS_INCONSISTENT);
        }

        @Test
        void allowsFirstVariantOnAProductWithNoExistingVariants() {
            Product product = productWithId(1); // no variants yet
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.existsBySku("TEE-S-BLK")).thenReturn(false);
            when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCommands.VariantInput input = new ProductCommands.VariantInput(
                    "TEE-S-BLK", BigDecimal.valueOf(24.99), 20, Map.of("size", "S"));

            assertThat(service.addVariant(1, input)).isNotNull();
        }

        @Test
        void rejectsAddedVariantAttributeNotInCategorySchema() {
            Product product = productWithId(1); // no variants yet — the category schema is the only check that can fire
            addCategoryAttribute(product.getProductCategory(), "size", false, "S", "M", "L");
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.existsBySku("TEE-1")).thenReturn(false);

            ProductCommands.VariantInput input = new ProductCommands.VariantInput(
                    "TEE-1", BigDecimal.valueOf(24.99), 20, Map.of("color", "Black"));

            assertThatThrownBy(() -> service.addVariant(1, input))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_ATTRIBUTE_NOT_ALLOWED_FOR_CATEGORY);
        }

        @Test
        void rejectsAddedVariantMissingARequiredCategoryAttribute() {
            Product product = productWithId(1);
            addCategoryAttribute(product.getProductCategory(), "size", true, "S", "M", "L");
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.existsBySku("TEE-1")).thenReturn(false);

            ProductCommands.VariantInput input = new ProductCommands.VariantInput(
                    "TEE-1", BigDecimal.valueOf(24.99), 20, Map.of());

            assertThatThrownBy(() -> service.addVariant(1, input))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_REQUIRED_ATTRIBUTE_MISSING);
        }
    }

    @Nested
    class RemoveVariant {

        @Test
        void removesVariantWhenMoreThanOneRemains() {
            Product product = productWithId(1);
            ProductVariant toRemove = variant(product, 100, "TEE-S-BLK", Map.of());
            ProductVariant keep = variant(product, 101, "TEE-M-BLK", Map.of());
            product.getVariants().add(toRemove);
            product.getVariants().add(keep);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.findById(100)).thenReturn(Optional.of(toRemove));

            service.removeVariant(1, 100);

            assertThat(product.getVariants()).containsExactly(keep);
            verify(productVariantRepository).delete(toRemove);
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        void rejectsRemovingTheLastVariant() {
            Product product = productWithId(1);
            ProductVariant onlyVariant = variant(product, 100, "TEE-S-BLK", Map.of());
            product.getVariants().add(onlyVariant);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.findById(100)).thenReturn(Optional.of(onlyVariant));

            assertThatThrownBy(() -> service.removeVariant(1, 100))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT);

            verify(productVariantRepository, never()).delete(any());
        }

        @Test
        void rejectsVariantBelongingToAnotherProduct() {
            Product product = productWithId(1);
            Product otherProduct = productWithId(2);
            ProductVariant otherVariant = variant(otherProduct, 200, "OTHER-SKU", Map.of());
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.findById(200)).thenReturn(Optional.of(otherVariant));

            assertThatThrownBy(() -> service.removeVariant(1, 200))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_VARIANT_BELONGS_TO_ANOTHER_PRODUCT);
        }

        @Test
        void throwsWhenVariantDoesNotExist() {
            Product product = productWithId(1);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productVariantRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeVariant(1, 999))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class AddImage {

        @Test
        void addsImageWhenSortOrderIsAvailable() {
            Product product = productWithId(1);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> {
                ProductImage img = invocation.getArgument(0);
                img.setId(200);
                return img;
            });

            ProductCommands.ImageInput input = new ProductCommands.ImageInput("a.jpg", 0);
            ProductImage result = service.addImage(1, input);

            assertThat(result.getId()).isEqualTo(200);
            assertThat(product.getImages()).hasSize(1);
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        void rejectsSortOrderAlreadyUsedByAnotherImage() {
            Product product = productWithId(1);
            product.getImages().add(image(product, 200, 0));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));

            ProductCommands.ImageInput input = new ProductCommands.ImageInput("b.jpg", 0);

            assertThatThrownBy(() -> service.addImage(1, input))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_IMAGE_SORT_ORDER_CONFLICT);
        }
    }

    @Nested
    class RemoveImage {

        @Test
        void removesImage() {
            Product product = productWithId(1);
            ProductImage toRemove = image(product, 200, 0);
            product.getImages().add(toRemove);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productImageRepository.findById(200)).thenReturn(Optional.of(toRemove));

            service.removeImage(1, 200);

            assertThat(product.getImages()).isEmpty();
            verify(productImageRepository).delete(toRemove);
        }

        @Test
        void rejectsImageBelongingToAnotherProduct() {
            Product product = productWithId(1);
            Product otherProduct = productWithId(2);
            ProductImage otherImage = image(otherProduct, 200, 0);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productImageRepository.findById(200)).thenReturn(Optional.of(otherImage));

            assertThatThrownBy(() -> service.removeImage(1, 200))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_IMAGE_BELONGS_TO_ANOTHER_PRODUCT);
        }
    }

    @Nested
    class UpdateImageSortOrderTests {

        @Test
        void movesImageToAFreeSortOrder() {
            Product product = productWithId(1);
            ProductImage toMove = image(product, 200, 0);
            ProductImage other = image(product, 201, 1);
            product.getImages().add(toMove);
            product.getImages().add(other);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productImageRepository.findById(200)).thenReturn(Optional.of(toMove));
            when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductImage result = service.updateImageSortOrder(1, 200, 2);

            assertThat(result.getSortOrder()).isEqualTo(2);
        }

        @Test
        void excludesTheImageBeingMovedFromItsOwnConflictCheck() {
            Product product = productWithId(1);
            ProductImage toMove = image(product, 200, 0);
            product.getImages().add(toMove);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productImageRepository.findById(200)).thenReturn(Optional.of(toMove));
            when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // "Moving" to the sort order it already has must not conflict with itself.
            ProductImage result = service.updateImageSortOrder(1, 200, 0);

            assertThat(result.getSortOrder()).isEqualTo(0);
        }

        @Test
        void rejectsMovingToASortOrderHeldByAnotherImage() {
            Product product = productWithId(1);
            ProductImage toMove = image(product, 200, 0);
            ProductImage other = image(product, 201, 1);
            product.getImages().add(toMove);
            product.getImages().add(other);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productImageRepository.findById(200)).thenReturn(Optional.of(toMove));

            assertThatThrownBy(() -> service.updateImageSortOrder(1, 200, 1))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_IMAGE_SORT_ORDER_CONFLICT);
        }
    }

    @Nested
    class UploadImage {

        @Test
        void uploadsThroughStorageServiceAndPersistsTheImage() {
            Product product = productWithId(1);
            MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(storageService.uploadImage(anyString(), eq(file))).thenReturn("products/1/generated.jpg");
            when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> {
                ProductImage img = invocation.getArgument(0);
                img.setId(300);
                return img;
            });

            ProductImage result = service.uploadImage(1, file, 0);

            assertThat(result.getStorageKey()).isEqualTo("products/1/generated.jpg");
            assertThat(product.getImages()).hasSize(1);
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        void rejectsSortOrderConflictWithoutEverCallingStorageService() {
            Product product = productWithId(1);
            product.getImages().add(image(product, 200, 0));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1});

            assertThatThrownBy(() -> service.uploadImage(1, file, 0))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_IMAGE_SORT_ORDER_CONFLICT);

            verify(storageService, never()).uploadImage(anyString(), any());
        }

        @Test
        void throwsWhenProductDoesNotExist() {
            when(productRepository.findById(99)).thenReturn(Optional.empty());
            MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1});

            assertThatThrownBy(() -> service.uploadImage(99, file, 0))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(storageService, never()).uploadImage(anyString(), any());
        }
    }
}
