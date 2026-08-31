package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderResponse;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * HTTP contract for a shopper's own orders (Epic 3 Phase 5, US-3.3/3.5/3.6). Authenticated-only,
 * same as {@code CartApi}/{@code CheckoutApi} — falls under this module's
 * {@code security/SecurityConfig}'s default {@code anyRequest().authenticated()} rule, no new rule
 * needed. Admin-only ship/deliver live on {@link AdminOrderApi} instead, under the existing
 * {@code /api/v1/admin/**} rule.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.OrderController})
 * carries no HTTP annotations, matching {@code content-service}'s {@code CategoryApi}/
 * {@code CategoryController} split.
 */
@RequestMapping("/api/v1/orders")
public interface OrderApi {

    /**
     * Paginated list of the caller's own orders, most recent first (US-3.5), optionally narrowed
     * to one of several statuses (the GUI's grouped status tabs — post-Epic-3 follow-up).
     *
     * @param userUuid the caller's Keycloak UUID
     * @param statuses optional status set to narrow to (repeated query param, e.g.
     *                 {@code ?statuses=PENDING&statuses=PAYMENT_PROCESSING}); omitted returns
     *                 every status ("All")
     * @param page     zero-based page number (default 0)
     * @param size     page size (default 20)
     * @return {@code 200} with a page of the caller's orders
     */
    @GetMapping
    ResponseEntity<PagedResponse<OrderResponse>> list(
            @CurrentUserId String userUuid,
            @RequestParam(required = false) List<OrderStatus> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    /**
     * Returns one of the caller's own orders, including its full status timeline (US-3.5).
     *
     * @param userUuid the caller's Keycloak UUID
     * @param id       order primary key
     * @return {@code 200} with the order
     */
    @GetMapping("/{id}")
    ResponseEntity<OrderResponse> getById(@CurrentUserId String userUuid, @PathVariable Integer id);

    /**
     * Cancels one of the caller's own orders (US-3.6) — the compensating action depends on the
     * order's current status (see {@code OrderService.cancel}'s own Javadoc).
     *
     * @param userUuid the caller's Keycloak UUID
     * @param id       order primary key
     * @return {@code 200} with the updated order
     */
    @PostMapping("/{id}/cancel")
    ResponseEntity<OrderResponse> cancel(@CurrentUserId String userUuid, @PathVariable Integer id);

    /**
     * Initiates the payment handoff for one of the caller's own {@code PENDING} orders (US-3.3) —
     * see {@code OrderService.initiatePayment}'s own Javadoc for why this may return with the order
     * still {@code PAYMENT_PROCESSING} rather than a definitive outcome.
     *
     * @param userUuid the caller's Keycloak UUID
     * @param id       order primary key
     * @return {@code 200} with the order after the gateway's verdict (or lack of one) is applied
     */
    @PostMapping("/{id}/pay")
    ResponseEntity<OrderResponse> pay(@CurrentUserId String userUuid, @PathVariable Integer id);
}
