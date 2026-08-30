package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderResponse;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for admin order-fulfillment (US-3.7/3.8, plus the {@link #list} queue endpoint
 * added once the admin GUI needed a way to find orders to act on). Admin-gated automatically via
 * this module's own {@code security/SecurityConfig} {@code /api/v1/admin/**} rule — no
 * method-level {@code @PreAuthorize} needed, same shape as {@link ProductApi}. A separate interface
 * from {@link OrderApi} (rather than folding these actions into it) mirrors this module's existing
 * {@code ProductCategoryApi}/{@code PublicProductCategoryApi} split: same underlying resource, but
 * a genuinely different audience and security rule per interface.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.AdminOrderController})
 * carries no HTTP annotations, matching {@code content-service}'s {@code CategoryApi}/
 * {@code CategoryController} split.
 */
@RequestMapping("/api/v1/admin/orders")
public interface AdminOrderApi {

    /**
     * Paginated list of orders for the fulfillment queue, optionally filtered by status —
     * {@code ?status=CONFIRMED} for "ready to ship", {@code ?status=SHIPPED} for "ready to mark
     * delivered". Sorted oldest-first (plain order id ascending) — a FIFO queue, not a
     * client-configurable sort; there's no admin need yet to sort a fulfillment queue any other way.
     *
     * @param status optional status filter; omitted returns every order regardless of status
     * @param page   zero-based page number (default 0)
     * @param size   page size (default 20)
     * @return {@code 200} with a page of orders
     */
    @GetMapping
    ResponseEntity<PagedResponse<OrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    /**
     * Marks an order shipped (US-3.7) — only valid from {@code CONFIRMED}.
     *
     * @param id order primary key
     * @return {@code 200} with the updated order
     */
    @PostMapping("/{id}/ship")
    ResponseEntity<OrderResponse> ship(@PathVariable Integer id);

    /**
     * Marks an order delivered (US-3.8) — only valid from {@code SHIPPED}; the terminal
     * happy-path state.
     *
     * @param id order primary key
     * @return {@code 200} with the updated order
     */
    @PostMapping("/{id}/deliver")
    ResponseEntity<OrderResponse> deliver(@PathVariable Integer id);
}
