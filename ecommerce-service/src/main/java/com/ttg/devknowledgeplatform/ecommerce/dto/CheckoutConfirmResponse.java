package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** REST response shape for {@code POST /api/v1/checkout/confirm} (US-2.6) — the created order. */
@Data
@Builder
public class CheckoutConfirmResponse {

    private Integer orderId;
    private OrderStatus status;
    private AddressResponse address;
    private List<OrderLineResponse> lines;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal total;
    /** Any cart line dropped at this final revalidation — normally empty; see {@code CheckoutResult}'s Javadoc. */
    private List<CartLineResponse> droppedLines;
}
