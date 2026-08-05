package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.outbox.OutboxEventHandler;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductSearchViewRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projects a {@code Product}'s current write-side state into its {@code ProductSearchView} row —
 * the {@code PRODUCT_CHANGED} handler registered with {@code OutboxEventDispatcher}.
 *
 * <p>Re-derives the entire row from current state rather than applying the event as a diff (see
 * {@code OutboxEvent}'s Javadoc for why) — always re-reads {@link Product} and its variants fresh
 * rather than trusting anything in the event payload beyond the id.
 *
 * <p>A deactivated (or missing) product gets its read-model row deleted, not updated: US-1.7 says
 * a deactivated product must "disappear from browse/search," and {@code ProductSearchView} has no
 * {@code active} flag of its own to filter on — a missing row *is* the not-visible state.
 *
 * <p>{@link #EVENT_TYPE} is {@code public} specifically so publishers (e.g.
 * {@code ProductServiceImpl}) reference this constant instead of retyping the literal
 * {@code "PRODUCT_CHANGED"} — two independent hardcoded copies of the same string have no
 * compiler link between them, so a typo in either one would silently break dispatch (the event
 * would just retry until {@code FAILED}, with no build-time warning). This is the per-handler
 * alternative to a shared {@code eventType} enum — see {@code OutboxEvent}'s Javadoc for why one
 * central enum spanning every future epic's event types is the wrong tradeoff here.
 *
 * <p>{@link Payload} is this handler's own payload shape, for the exact same reason: a shared
 * payload DTO across every event type would recreate the "every future epic edits the same file"
 * problem {@code eventType} already avoids by staying per-handler, since a payment or review
 * event's payload will carry entirely different fields than this one's. Producer
 * ({@code ProductServiceImpl}) and consumer (this class) both go through {@link Payload} instead
 * of independently agreeing on the {@code "productId"} map key and its {@code Number}-to-
 * {@code Integer} cast.
 */
@Component
@RequiredArgsConstructor
public class ProductChangedOutboxEventHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "PRODUCT_CHANGED";

    private final ProductRepository productRepository;
    private final ProductSearchViewRepository productSearchViewRepository;

    /** Typed payload for {@code PRODUCT_CHANGED} events — see the class Javadoc for why this is per-handler. */
    public record Payload(Integer productId) {

        public Map<String, Object> toMap() {
            return Map.of("productId", productId);
        }

        public static Payload from(OutboxEvent event) {
            Number productId = (Number) event.getPayload().get("productId");
            return new Payload(productId.intValue());
        }
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxEvent event) {
        Integer productId = Payload.from(event).productId();
        Product product = productRepository.findById(productId).orElse(null);

        if (product == null || !product.isActive() || product.getVariants().isEmpty()) {
            productSearchViewRepository.deleteByProductId(productId);
            return;
        }

        List<ProductVariant> variants = product.getVariants();
        BigDecimal minPrice = variants.stream().map(ProductVariant::getPrice).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal maxPrice = variants.stream().map(ProductVariant::getPrice).max(Comparator.naturalOrder()).orElseThrow();
        boolean inStock = variants.stream()
                .anyMatch(v -> v.getStockQuantity() - v.getReservedQuantity() > 0);

        Map<String, List<String>> availableAttributes = collectAvailableAttributes(variants);
        String searchText = product.getDescription() == null
                ? product.getName()
                : product.getName() + " " + product.getDescription();
        String primaryImageStorageKey = product.getImages().stream()
                .min(Comparator.comparing(ProductImage::getSortOrder))
                .map(ProductImage::getStorageKey)
                .orElse(null);

        ProductSearchView view = productSearchViewRepository.findByProductId(productId)
                .orElseGet(ProductSearchView::new);
        view.setProduct(product);
        view.setName(product.getName());
        view.setSlug(product.getSlug());
        view.setProductCategoryId(product.getProductCategory().getId());
        view.setCategoryName(product.getProductCategory().getName());
        view.setMinPrice(minPrice);
        view.setMaxPrice(maxPrice);
        view.setInStock(inStock);
        view.setPrimaryImageStorageKey(primaryImageStorageKey);
        view.setSearchText(searchText);
        view.setAvailableAttributes(availableAttributes);

        productSearchViewRepository.save(view);
    }

    /** Distinct values per attribute key across every variant — see {@code ProductSearchView}'s Javadoc. */
    private static Map<String, List<String>> collectAvailableAttributes(List<ProductVariant> variants) {
        Map<String, Set<String>> distinctValuesByKey = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            variant.getAttributes().forEach((key, value) ->
                    distinctValuesByKey.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(value));
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        distinctValuesByKey.forEach((key, values) -> result.put(key, new ArrayList<>(values)));
        return result;
    }
}
