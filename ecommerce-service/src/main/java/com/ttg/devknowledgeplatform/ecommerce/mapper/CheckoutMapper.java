package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.AddressResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CheckoutConfirmResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CheckoutPreviewResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderLineResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.Address;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutPreview;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutResult;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Maps {@link CheckoutPreview}/{@link CheckoutResult} (this module's own plain domain records) to
 * their REST response shapes.
 *
 * <p>Hand-written, not MapStruct — like {@link CartMapper}, which it reuses ({@link
 * CartMapper#toLineResponse}) for every cart-line shape this mapper surfaces (a preview's lines,
 * and any lines dropped at confirm time), so the "unavailable line → most fields null" branching
 * lives in exactly one place.
 */
@Component
@RequiredArgsConstructor
public class CheckoutMapper {

    private final CartMapper cartMapper;

    public CheckoutPreviewResponse toPreviewResponse(CheckoutPreview preview) {
        return CheckoutPreviewResponse.builder()
                .lines(preview.lines().stream().map(cartMapper::toLineResponse).toList())
                .subtotal(preview.subtotal())
                .shippingFee(preview.shippingFee())
                .total(preview.total())
                .build();
    }

    public CheckoutConfirmResponse toConfirmResponse(CheckoutResult result) {
        Order order = result.order();
        return CheckoutConfirmResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .address(toAddressResponse(order.getShippingAddress()))
                .lines(order.getLines().stream().map(CheckoutMapper::toOrderLineResponse).toList())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .total(order.getTotal())
                .droppedLines(result.droppedLines().stream().map(cartMapper::toLineResponse).toList())
                .build();
    }

    private static OrderLineResponse toOrderLineResponse(OrderLine line) {
        return OrderLineResponse.builder()
                .variantId(line.getProductVariantId())
                .sku(line.getSku())
                .productName(line.getProductName())
                .unitPrice(line.getUnitPrice())
                .quantity(line.getQuantity())
                .lineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
                .build();
    }

    private static AddressResponse toAddressResponse(Address address) {
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
}
