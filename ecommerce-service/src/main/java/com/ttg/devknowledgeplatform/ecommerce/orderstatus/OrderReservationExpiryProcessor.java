package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.infra.scheduling.AbstractGuardedUpdateProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

/**
 * Expires one order's reservation (US-3.2), on infra's {@link AbstractGuardedUpdateProcessor}: load,
 * re-check, transition, save — in one transaction, via the inherited {@code process(id)}. Kept as its
 * own bean, not a second method on {@link OrderReservationExpiryJob} — the identical reasoning as
 * {@code outbox.OutboxEventProcessor}'s own Javadoc: Spring's {@code @Transactional} is
 * proxy-based, so a bean calling its own {@code @Transactional} method via {@code this.foo()}
 * bypasses the proxy and silently runs with no transaction.
 *
 * <p>The re-check ({@link #isStillEligible}) is a defensive guard against a stale id in the poll
 * batch (e.g. the shopper cancelled it themselves in the gap between the poll query and this call),
 * backed by {@code Order}'s own {@code @Version} for the remaining window — not a
 * distributed-concurrency mechanism: this reactor runs exactly one instance of each service today
 * (see root {@code CLAUDE.md}'s Routing section), so unlike {@code OutboxEventRepository.claim}, no
 * atomic claim-style {@code UPDATE} is needed here — add one if this job is ever run with more than
 * one instance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReservationExpiryProcessor extends AbstractGuardedUpdateProcessor<Order, Integer> {

    private final OrderRepository orderRepository;
    private final OrderStatusHandlerRegistry orderStatusHandlerRegistry;

    @Override
    protected CrudRepository<Order, Integer> repository() {
        return orderRepository;
    }

    /** Only a still-{@code PENDING} order holds a reservation to expire. */
    @Override
    protected boolean isStillEligible(Order order) {
        return order.getStatus() == OrderStatus.PENDING;
    }

    @Override
    protected void apply(Order order) {
        orderStatusHandlerRegistry.expire(order);
        log.info("Expired reservation for order id={}", order.getId());
    }
}
