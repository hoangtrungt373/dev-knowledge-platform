package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for {@link Order}. {@link JpaSpecificationExecutor} backs both the admin fulfillment
 * queue's optional status filter (US-3.7/3.8) and the shopper-facing order-history status tabs
 * (post-Epic-3 follow-up) — see {@code repository.spec.OrderSpecification}, this module's usual
 * Specification-pattern home for dynamic filtering (never a hand-built JPQL string). A shopper's
 * own orders are backed by {@code IDX_CUSTOMER_ORDER_OWNER_UUID} either way — this repository used
 * to expose a dedicated {@code findByOwnerUuidOrderByIdDesc} derived query for that case before the
 * status-tabs feature needed an optional {@code IN} filter alongside the ownership check, at which
 * point {@code OrderServiceImpl.listOrders} moved onto {@code findAll(Specification, Pageable)}
 * (same as the admin queue already used) and that derived query was deleted as dead code.
 */
public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {

    /**
     * Ids of every order in {@code status} created before {@code cutoff} — the reservation-expiry
     * job's (US-3.2) own poll query, same shape as {@code OutboxEventRepository.findIdsByStatus}.
     */
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.dteCreation < :cutoff ORDER BY o.id ASC")
    List<Integer> findIdsByStatusAndDteCreationBefore(
            @Param("status") OrderStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Ids of every order in {@code status} whose payment-processing clock started before
     * {@code cutoff} — the reconciliation job's (US-3.4) own poll query, for orders stuck in
     * {@code PAYMENT_PROCESSING} beyond a grace period.
     */
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.paymentProcessingStartedAt < :cutoff ORDER BY o.id ASC")
    List<Integer> findIdsByStatusAndPaymentProcessingStartedAtBefore(
            @Param("status") OrderStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);
}
