package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * REST response shape for one {@code OrderStatusHistory} row (US-3.5) — one entry in an order's
 * timeline. {@code fromStatus} is {@code null} for the very first entry (order creation).
 */
@Data
@Builder
public class OrderStatusHistoryResponse {

    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private String reason;
    private Instant occurredAt;
}
