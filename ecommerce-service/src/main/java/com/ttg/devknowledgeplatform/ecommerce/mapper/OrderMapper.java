package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.AddressResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderLineResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderStatusHistoryResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.Address;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderStatusHistory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.infra.service.StorageService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Maps {@link Order} (and its {@link OrderLine}/{@link OrderStatusHistory}/{@link Address}
 * children) to REST response shapes (Epic 3 Phase 5, US-3.5).
 *
 * <p>Hand-written, not MapStruct — same as {@link CartMapper}/{@link CheckoutMapper} in this
 * package. {@link #toOrderLineResponse} is no longer {@code static} (a post-Epic-3 follow-up made
 * it resolve the purchased variant's current attributes/image live, which needs
 * {@link ProductVariantRepository}/{@link StorageService} — same live-image-resolution shape
 * {@link CartMapper#toLineResponse} already uses for cart lines) — {@link CheckoutMapper} now
 * injects this class instead of calling a static method, so it can still reuse this one method for
 * its own {@code CheckoutConfirmResponse} instead of duplicating identical {@code OrderLine}
 * mapping logic. {@link #toAddressResponse} stays {@code public static}, unaffected — it needs no
 * lookup of its own.
 *
 * <p>{@link #toResponse} reads {@link Order#getLines()}/{@link Order#getStatusHistory()}, both
 * lazy collections — this only works because Spring Boot's {@code spring.jpa.open-in-view} default
 * (unchanged in this module) keeps the Hibernate session open for the whole request, the same
 * reliance {@code ProductMapper} already has on {@code Product.variants}/{@code images}.
 */
@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final ProductVariantRepository productVariantRepository;
    private final StorageService storageService;

    public OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .cancelRequested(order.getCancelRequested())
                .shippingAddress(toAddressResponse(order.getShippingAddress()))
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .total(order.getTotal())
                .lines(order.getLines().stream().map(this::toOrderLineResponse).toList())
                .statusHistory(order.getStatusHistory().stream().map(OrderMapper::toHistoryResponse).toList())
                .build();
    }

    /**
     * Maps one {@link OrderLine}, plus a best-effort live lookup of its variant's current
     * attributes/primary image/product slug by {@link OrderLine#getProductVariantId()} — all
     * {@code null} if that variant (or its product) no longer exists, since
     * {@code productVariantId} is a plain column, not a real foreign key (see {@code OrderLine}'s
     * own Javadoc). {@code productSlug} lets the GUI link the line back to its product page.
     */
    public OrderLineResponse toOrderLineResponse(OrderLine line) {
        OrderLineResponse.OrderLineResponseBuilder builder = OrderLineResponse.builder()
                .variantId(line.getProductVariantId())
                .sku(line.getSku())
                .productName(line.getProductName())
                .unitPrice(line.getUnitPrice())
                .quantity(line.getQuantity())
                .lineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));

        productVariantRepository.findById(line.getProductVariantId()).ifPresent(variant ->
                builder.attributes(variant.getAttributes())
                        .primaryImageUrl(resolvePrimaryImageUrl(variant))
                        .productSlug(variant.getProduct().getSlug()));

        return builder.build();
    }

    /**
     * Resolves the variant's product's first gallery image into a presigned URL — see
     * {@link ProductImageUrls#resolvePrimaryImageUrl} for the shared logic (also used by
     * {@link CartMapper#toLineResponse}).
     */
    private String resolvePrimaryImageUrl(ProductVariant variant) {
        return ProductImageUrls.resolvePrimaryImageUrl(variant.getProduct(), storageService);
    }

    public static AddressResponse toAddressResponse(Address address) {
        return AddressResponse.builder()
                .fullName(address.getFullName())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }

    private static OrderStatusHistoryResponse toHistoryResponse(OrderStatusHistory history) {
        return OrderStatusHistoryResponse.builder()
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .reason(history.getReason())
                .occurredAt(history.getDteCreation())
                .build();
    }
}
