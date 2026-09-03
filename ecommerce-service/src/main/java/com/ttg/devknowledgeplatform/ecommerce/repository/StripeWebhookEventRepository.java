package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.StripeWebhookEvent;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link StripeWebhookEvent} — the at-least-once webhook-delivery dedup ledger
 * (US-4.5). {@link #existsByStripeEventId} is the whole mechanism: cheap existence check before
 * doing any real work, and the row this same transaction then inserts is what makes a later
 * redelivery of the same event id see {@code true} here instead of reprocessing.
 */
public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, Integer> {

    boolean existsByStripeEventId(String stripeEventId);
}
