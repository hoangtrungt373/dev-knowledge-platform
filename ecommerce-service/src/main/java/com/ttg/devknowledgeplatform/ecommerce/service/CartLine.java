package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;

/**
 * One resolved line in a shopper's cart — a Redis-stored {@code variantId -> quantity} pair,
 * joined live against the catalog (US-2.3) at read time.
 *
 * <p>{@link #variant} is {@code null} when {@link #available} is {@code false} — the variant (or
 * its parent product) no longer exists/is active (US-2.7), but the line is still surfaced (with
 * whatever {@link #variantId}/{@link #quantity} Redis still has) rather than silently dropped, so
 * the shopper can see something changed and choose to remove it. {@code CartMapper}/checkout are
 * both responsible for excluding an unavailable line from totals — this record doesn't compute
 * anything itself, only carries the resolved facts.
 */
public record CartLine(Integer variantId, int quantity, boolean available, ProductVariant variant) {

    public static CartLine available(Integer variantId, int quantity, ProductVariant variant) {
        return new CartLine(variantId, quantity, true, variant);
    }

    public static CartLine unavailable(Integer variantId, int quantity) {
        return new CartLine(variantId, quantity, false, null);
    }
}
