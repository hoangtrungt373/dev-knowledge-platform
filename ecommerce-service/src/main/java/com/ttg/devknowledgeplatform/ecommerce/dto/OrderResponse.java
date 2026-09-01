package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST response shape for a shopper's order (Epic 3 Phase 5, US-3.5) — current status, its lines,
 * and its full transition history in one shape, reused for both the list-mine and get-by-id
 * endpoints (same "one response DTO for list and detail" convention {@code ProductResponse}
 * already established in this module).
 */
@Data
@Builder
public class OrderResponse {

    private Integer id;
    private OrderStatus status;
    private Boolean cancelRequested;
    private AddressResponse shippingAddress;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    /** What {@code shippingFee} would have been absent any promotional waiver — equal to
     * {@code shippingFee} whenever nothing was waived; see {@code Order.originalShippingFee}'s
     * own Javadoc. */
    private BigDecimal originalShippingFee;
    private BigDecimal total;
    private List<OrderLineResponse> lines;
    private List<OrderStatusHistoryResponse> statusHistory;
}
