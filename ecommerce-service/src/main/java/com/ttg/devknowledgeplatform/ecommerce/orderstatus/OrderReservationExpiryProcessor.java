package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires one order's reservation (US-3.2). Kept as its own bean, not a second method on
 * {@link OrderReservationExpiryJob} — the identical reasoning as
 * {@code outbox.OutboxEventProcessor}'s own Javadoc: Spring's {@code @Transactional} is
 * proxy-based, so a bean calling its own {@code @Transactional} method via {@code this.foo()}
 * bypasses the proxy and silently runs with no transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReservationExpiryProcessor {

    private final OrderRepository orderRepository;
    private final OrderStatusHandlerRegistry orderStatusHandlerRegistry;

    /**
     * Re-checks {@code order}'s status after loading it — a defensive guard against a stale id in
     * the poll batch (e.g. the shopper cancelled it themselves in the gap between the poll query
     * and this call), not a distributed-concurrency mechanism: this reactor runs exactly one
     * instance of each service today (see root {@code CLAUDE.md}'s Routing section), so unlike
     * {@code OutboxEventRepository.claim}, no atomic claim-style {@code UPDATE} is needed here —
     * add one if this job is ever run with more than one instance.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void expireOne(Integer orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.PENDING) {
                return;
            }
            orderStatusHandlerRegistry.expire(order);
            orderRepository.save(order);
            log.info("Expired reservation for order id={}", orderId);
        });
    }
}
