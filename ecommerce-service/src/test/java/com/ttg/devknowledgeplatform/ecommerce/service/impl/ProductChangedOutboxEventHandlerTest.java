package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductSearchViewRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductChangedOutboxEventHandler} — the projection that gets a product
 * into {@link ProductSearchView} at all (US-1.5's CQRS read model) and enforces US-1.7's
 * "deactivated products disappear from browse/search" rule.
 */
@ExtendWith(MockitoExtension.class)
class ProductChangedOutboxEventHandlerTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductSearchViewRepository productSearchViewRepository;

    @InjectMocks
    private ProductChangedOutboxEventHandler handler;

    private static OutboxEvent eventFor(Integer productId) {
        OutboxEvent event = new OutboxEvent();
        event.setPayload(Map.of("productId", productId));
        return event;
    }

    private static ProductCategory category() {
        ProductCategory category = new ProductCategory();
        category.setId(10);
        category.setName("Apparel");
        return category;
    }

    private static Product activeProductWith(ProductVariant... variants) {
        Product product = new Product();
        product.setId(1);
        product.setName("404 Not Found T-Shirt");
        product.setSlug("404-not-found-t-shirt");
        product.setActive(true);
        product.setProductCategory(category());
        for (ProductVariant variant : variants) {
            variant.setProduct(product);
            product.getVariants().add(variant);
        }
        return product;
    }

    private static ProductVariant variant(BigDecimal price, int stock, int reserved, Map<String, String> attributes) {
        ProductVariant variant = new ProductVariant();
        variant.setPrice(price);
        variant.setStockQuantity(stock);
        variant.setReservedQuantity(reserved);
        variant.setAttributes(attributes);
        return variant;
    }

    private static ProductImage image(Product product, String storageKey, int sortOrder) {
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setStorageKey(storageKey);
        image.setSortOrder(sortOrder);
        return image;
    }

    @Test
    void eventTypeIsProductChanged() {
        assertThat(handler.eventType()).isEqualTo("PRODUCT_CHANGED");
    }

    @Nested
    class RowDeletion {

        @Test
        void deletesRowWhenProductNoLongerExists() {
            when(productRepository.findById(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            verify(productSearchViewRepository).deleteByProductId(1);
            verify(productSearchViewRepository, never()).save(any());
        }

        @Test
        void deletesRowWhenProductIsInactive() {
            Product product = activeProductWith(variant(BigDecimal.TEN, 5, 0, Map.of()));
            product.setActive(false);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));

            handler.handle(eventFor(1));

            verify(productSearchViewRepository).deleteByProductId(1);
        }

        @Test
        void deletesRowWhenProductHasNoVariants() {
            Product product = activeProductWith(); // no variants
            when(productRepository.findById(1)).thenReturn(Optional.of(product));

            handler.handle(eventFor(1));

            verify(productSearchViewRepository).deleteByProductId(1);
        }
    }

    @Nested
    class Projection {

        @Test
        void createsANewRowWhenNoneExistsYet() {
            Product product = activeProductWith(variant(BigDecimal.valueOf(24.99), 10, 0, Map.of()));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            ProductSearchView view = captor.getValue();
            assertThat(view.getName()).isEqualTo("404 Not Found T-Shirt");
            assertThat(view.getProductCategoryId()).isEqualTo(10);
            assertThat(view.getCategoryName()).isEqualTo("Apparel");
        }

        @Test
        void updatesTheExistingRowInPlaceRatherThanCreatingASecondOne() {
            Product product = activeProductWith(variant(BigDecimal.valueOf(24.99), 10, 0, Map.of()));
            ProductSearchView existingView = new ProductSearchView();
            existingView.setId(500);
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.of(existingView));

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(500);
        }

        @Test
        void minAndMaxPriceSpanEveryVariant() {
            Product product = activeProductWith(
                    variant(BigDecimal.valueOf(10), 5, 0, Map.of()),
                    variant(BigDecimal.valueOf(30), 5, 0, Map.of()),
                    variant(BigDecimal.valueOf(20), 5, 0, Map.of()));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            assertThat(captor.getValue().getMinPrice()).isEqualByComparingTo("10");
            assertThat(captor.getValue().getMaxPrice()).isEqualByComparingTo("30");
        }

        @Test
        void inStockIsTrueWhenAtLeastOneVariantHasAvailableStock() {
            Product product = activeProductWith(
                    variant(BigDecimal.TEN, 0, 0, Map.of()), // sold out
                    variant(BigDecimal.TEN, 5, 2, Map.of())); // 3 available
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            assertThat(captor.getValue().isInStock()).isTrue();
        }

        @Test
        void inStockIsFalseWhenEveryVariantIsFullyReservedOrEmpty() {
            Product product = activeProductWith(
                    variant(BigDecimal.TEN, 0, 0, Map.of()),
                    variant(BigDecimal.TEN, 5, 5, Map.of())); // fully reserved
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            assertThat(captor.getValue().isInStock()).isFalse();
        }

        @Test
        void primaryImageIsTheLowestSortOrderImage() {
            Product product = activeProductWith(variant(BigDecimal.TEN, 5, 0, Map.of()));
            product.getImages().add(image(product, "second.jpg", 1));
            product.getImages().add(image(product, "first.jpg", 0));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            assertThat(captor.getValue().getPrimaryImageStorageKey()).isEqualTo("first.jpg");
        }

        @Test
        void primaryImageIsNullWhenTheProductHasNoImagesYet() {
            Product product = activeProductWith(variant(BigDecimal.TEN, 5, 0, Map.of()));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            assertThat(captor.getValue().getPrimaryImageStorageKey()).isNull();
        }

        @Test
        void availableAttributesCollectDistinctValuesPerKeyAcrossEveryVariant() {
            Product product = activeProductWith(
                    variant(BigDecimal.TEN, 5, 0, Map.of("size", "S", "color", "Black")),
                    variant(BigDecimal.TEN, 5, 0, Map.of("size", "M", "color", "Black")),
                    variant(BigDecimal.TEN, 5, 0, Map.of("size", "S", "color", "White")));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            Map<String, java.util.List<String>> attributes = captor.getValue().getAvailableAttributes();
            assertThat(attributes.get("size")).containsExactlyInAnyOrder("S", "M");
            assertThat(attributes.get("color")).containsExactlyInAnyOrder("Black", "White");
        }

        @Test
        void searchTextConcatenatesNameAndDescriptionWhenBothArePresent() {
            Product product = activeProductWith(variant(BigDecimal.TEN, 5, 0, Map.of()));
            product.setDescription("A tee for empty search results");
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            assertThat(captor.getValue().getSearchText()).isEqualTo("404 Not Found T-Shirt A tee for empty search results");
        }

        @Test
        void searchTextIsJustTheNameWhenDescriptionIsNull() {
            Product product = activeProductWith(variant(BigDecimal.TEN, 5, 0, Map.of()));
            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

            handler.handle(eventFor(1));

            ArgumentCaptor<ProductSearchView> captor = ArgumentCaptor.forClass(ProductSearchView.class);
            verify(productSearchViewRepository).save(captor.capture());
            assertThat(captor.getValue().getSearchText()).isEqualTo("404 Not Found T-Shirt");
        }
    }

    @Test
    void neverTrustsPayloadBeyondTheProductIdAndAlwaysReReadsCurrentState() {
        // Even though re-derivation logic can't be "tricked" via the payload (Payload only ever
        // carries productId), this pins the contract: handle() re-fetches Product fresh via the
        // repository rather than ever reading anything else off the event.
        Product product = activeProductWith(variant(BigDecimal.TEN, 5, 0, Map.of()));
        when(productRepository.findById(eq(1))).thenReturn(Optional.of(product));
        when(productSearchViewRepository.findByProductId(1)).thenReturn(Optional.empty());

        handler.handle(eventFor(1));

        verify(productRepository).findById(1);
    }
}
