package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Order}. No {@code findByOwnerUuid}/"list my orders" query yet — Epic 2's
 * scope is checkout (order creation) only; a shopper-facing order-history view belongs to a later
 * epic, added here when that's actually built rather than speculatively now.
 */
public interface OrderRepository extends JpaRepository<Order, Integer> {
}
