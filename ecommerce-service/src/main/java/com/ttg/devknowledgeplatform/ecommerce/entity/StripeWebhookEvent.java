package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A dedup ledger row for one already-processed Stripe webhook event (US-4.5) — Stripe delivers
 * webhooks at-least-once, so a redelivery of the same event id must be recognized and skipped
 * rather than reprocessed. {@link #stripeEventId} is enforced unique at the DB level
 * ({@code UX_STRIPE_WEBHOOK_EVENT_STRIPE_EVENT_ID}), so
 * {@code webhook.StripeWebhookService}'s own existence check plus this row's insert, both inside
 * the same transaction as the {@code Payment}/{@code Order} update it guards, is what actually
 * makes a redelivery a safe no-op rather than a double-application of the same outcome.
 *
 * <p>Deliberately its own small table, not folded into {@link Payment} — a single {@code Payment}
 * row can legitimately receive more than one distinct Stripe event over its lifetime (e.g. a
 * {@code payment_intent.succeeded} now, a refund-related event later), so "one row per event id"
 * and "one row per payment attempt" are different cardinalities entirely.
 */
@Entity
@Table(name = "STRIPE_WEBHOOK_EVENT", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "STRIPE_WEBHOOK_EVENT_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class StripeWebhookEvent extends AbstractEntity {

    @Column(name = "STRIPE_EVENT_ID", length = 255, nullable = false, unique = true)
    private String stripeEventId;

    @Column(name = "EVENT_TYPE", length = 100, nullable = false)
    private String eventType;
}
