package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.ecommerce.dto.OrderResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for admin order-fulfillment actions (Epic 3 Phase 5, US-3.7/3.8). Admin-gated
 * automatically via this module's own {@code security/SecurityConfig} {@code /api/v1/admin/**}
 * rule — no method-level {@code @PreAuthorize} needed, same shape as {@link ProductApi}. A separate
 * interface from {@link OrderApi} (rather than folding these two actions into it) mirrors this
 * module's existing {@code ProductCategoryApi}/{@code PublicProductCategoryApi} split: same
 * underlying resource, but a genuinely different audience and security rule per interface.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.AdminOrderController})
 * carries no HTTP annotations, matching {@code content-service}'s {@code CategoryApi}/
 * {@code CategoryController} split.
 */
@RequestMapping("/api/v1/admin/orders")
public interface AdminOrderApi {

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
