package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for {@link Order}. No {@code findByOwnerUuid}/"list my orders" query yet — that's
 * Epic 3 Phase 5's (the shopper-facing REST surface) job, added here when it's actually built
 * rather than speculatively now.
 */
public interface OrderRepository extends JpaRepository<Order, Integer> {

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
