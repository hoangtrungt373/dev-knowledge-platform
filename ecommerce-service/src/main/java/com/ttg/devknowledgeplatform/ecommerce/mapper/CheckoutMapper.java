package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.CheckoutConfirmResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CheckoutPreviewResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutPreview;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutResult;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Maps {@link CheckoutPreview}/{@link CheckoutResult} (this module's own plain domain records) to
 * their REST response shapes.
 *
 * <p>Hand-written, not MapStruct — like {@link CartMapper}, which it reuses ({@link
 * CartMapper#toLineResponse}) for every cart-line shape this mapper surfaces (a preview's lines,
 * and any lines dropped at confirm time), so the "unavailable line → most fields null" branching
 * lives in exactly one place. {@link OrderMapper#toOrderLineResponse}/{@link
 * OrderMapper#toAddressResponse} are reused the same way for the confirmed order's own lines/
 * address (Epic 3 Phase 5 introduced {@link OrderMapper} as the canonical owner of that mapping,
 * once a second caller needed it — this class no longer keeps its own copy).
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
                .address(OrderMapper.toAddressResponse(order.getShippingAddress()))
                .lines(order.getLines().stream().map(OrderMapper::toOrderLineResponse).toList())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .total(order.getTotal())
                .droppedLines(result.droppedLines().stream().map(cartMapper::toLineResponse).toList())
                .build();
    }
}
