package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.AddressResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderLineResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderStatusHistoryResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.Address;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderStatusHistory;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Maps {@link Order} (and its {@link OrderLine}/{@link OrderStatusHistory}/{@link Address}
 * children) to REST response shapes (Epic 3 Phase 5, US-3.5).
 *
 * <p>Hand-written, not MapStruct — same as {@link CartMapper}/{@link CheckoutMapper} in this
 * package. {@link #toOrderLineResponse}/{@link #toAddressResponse} are public static so
 * {@link CheckoutMapper} can reuse them for its own {@code CheckoutConfirmResponse} instead of
 * duplicating identical {@code OrderLine}/{@code Address} mapping logic — the same "extract once,
 * share" shape {@link CartMapper#toLineResponse} already established for cart lines.
 *
 * <p>{@link #toResponse} reads {@link Order#getLines()}/{@link Order#getStatusHistory()}, both
 * lazy collections — this only works because Spring Boot's {@code spring.jpa.open-in-view} default
 * (unchanged in this module) keeps the Hibernate session open for the whole request, the same
 * reliance {@code ProductMapper} already has on {@code Product.variants}/{@code images}.
 */
@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .cancelRequested(order.getCancelRequested())
                .shippingAddress(toAddressResponse(order.getShippingAddress()))
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .total(order.getTotal())
                .lines(order.getLines().stream().map(OrderMapper::toOrderLineResponse).toList())
                .statusHistory(order.getStatusHistory().stream().map(OrderMapper::toHistoryResponse).toList())
                .build();
    }

    public static OrderLineResponse toOrderLineResponse(OrderLine line) {
        return OrderLineResponse.builder()
                .variantId(line.getProductVariantId())
                .sku(line.getSku())
                .productName(line.getProductName())
                .unitPrice(line.getUnitPrice())
                .quantity(line.getQuantity())
                .lineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
                .build();
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
