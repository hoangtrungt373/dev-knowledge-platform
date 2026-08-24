package com.ttg.devknowledgeplatform.ecommerce.enums;

/**
 * Lifecycle status of an {@link com.ttg.devknowledgeplatform.ecommerce.entity.Order}.
 *
 * <p>Only {@link #PENDING} exists today — Epic 2's own responsibility ends at order creation (see
 * {@code docs/user-stories/02-cart-checkout.md}, US-2.6); Epic 3's reservation step and Epic 4's
 * payment step will each add the status values they need (e.g. {@code CONFIRMED}/{@code PAID}/
 * {@code CANCELLED}) when those epics are actually built, rather than speculatively added here now.
 */
public enum OrderStatus {
    PENDING
}
