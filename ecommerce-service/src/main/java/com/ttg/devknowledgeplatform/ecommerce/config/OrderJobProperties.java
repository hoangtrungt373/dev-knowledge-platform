package com.ttg.devknowledgeplatform.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code app.ecommerce.order.*} — a code-quality-audit finding, same shape as
 * {@link PaymentProperties}'s own (see that class's Javadoc): {@code reservation-timeout} and
 * {@code reconciliation.grace-period} used to be two separate {@code @Value}-injected {@link
 * Duration} fields on {@code OrderReservationExpiryJob}/{@code OrderReconciliationJob}
 * respectively. Each poller's own {@code poll-interval} (e.g.
 * {@code app.ecommerce.order.expiry-check.poll-interval}) deliberately stays a raw
 * {@code ${...}} placeholder on its {@code @Scheduled(fixedDelayString = ...)} annotation rather
 * than moving here — Spring's scheduling infrastructure resolves that placeholder directly off the
 * environment at schedule-registration time, not off a bound {@code @ConfigurationProperties}
 * instance, so consolidating it here would only be cosmetic at best and actively wrong if this
 * class were ever refactored to read the value a different way.
 */
@ConfigurationProperties(prefix = "app.ecommerce.order")
public record OrderJobProperties(Duration reservationTimeout, Reconciliation reconciliation) {

    public OrderJobProperties {
        if (reservationTimeout == null) {
            reservationTimeout = Duration.ofMinutes(15);
        }
        if (reconciliation == null) {
            reconciliation = new Reconciliation(null, null);
        }
    }

    /**
     * {@code app.ecommerce.order.reconciliation.*}. {@code abandonmentTimeout} is a follow-up,
     * deliberately much longer than {@code gracePeriod} — that one is about "did the gateway
     * resolve this yet" (checked on every poll tick once past it), this one is about "has the
     * shopper genuinely given up" ({@code OrderReconciliationJob} only actively cancels a
     * still-open PaymentIntent once an order has sat stuck past this second, longer threshold —
     * see that job's own Javadoc).
     */
    public record Reconciliation(Duration gracePeriod, Duration abandonmentTimeout) {

        public Reconciliation {
            if (gracePeriod == null) {
                gracePeriod = Duration.ofMinutes(2);
            }
            if (abandonmentTimeout == null) {
                abandonmentTimeout = Duration.ofMinutes(45);
            }
        }
    }
}
