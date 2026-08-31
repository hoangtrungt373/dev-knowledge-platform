package com.ttg.devknowledgeplatform.ecommerce.service;

import java.util.List;

/**
 * Drives Epic 2's checkout flow (US-2.5–2.7) on top of {@link CartService} — this epic's own
 * responsibility ends at order creation (Epic 3's reservation step and Epic 4's payment step pick
 * up from there once they're built).
 *
 * <p>Two-step flow: {@link #preview} revalidates the current cart and shows the shopper what
 * confirming right now would produce (US-2.6's "review... before confirming"); {@link #confirm}
 * re-validates fresh (never trusts a client-cached preview) and creates the {@link
 * com.ttg.devknowledgeplatform.ecommerce.entity.Order}. Both steps silently exclude any line that
 * fails the same existence/{@code active} check {@code CartService} already applies (US-2.7) —
 * the two-call flow is what gives the shopper a chance to see a dropped line before confirming;
 * neither call requires the client to explicitly re-acknowledge a prior preview's contents.
 *
 * <p><strong>{@code selectedVariantIds} (post-Epic-2 follow-up)</strong>: both methods accept an
 * optional subset of the cart's variant ids to restrict this checkout attempt to. {@code null}
 * means "the whole cart," exactly this interface's original behavior before this parameter
 * existed — every existing caller (and every already-placed order) is unaffected. When present,
 * a line whose variant id isn't in the set is treated the same as if it weren't in the cart at
 * all for this call: excluded from the totals/order, and — the actual point of this feature —
 * left untouched in the cart afterward by {@link #confirm}, rather than wiped along with
 * whatever was actually ordered.
 */
public interface CheckoutService {

    /**
     * Revalidates the caller's cart and computes what confirming it right now would produce.
     *
     * @param userUuid           the caller's Keycloak UUID
     * @param selectedVariantIds optional subset of variant ids to restrict this preview to;
     *                           {@code null} previews the whole cart
     * @return the reviewable preview
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the cart (or the
     *         selected subset of it) is empty, or every line in it is currently unavailable
     */
    CheckoutPreview preview(String userUuid, List<Integer> selectedVariantIds);

    /**
     * Creates an {@code Order} from the caller's currently-available cart lines and the given
     * shipping address, then removes only the lines actually ordered from the cart — only once
     * the order has been saved successfully, never before (US-2.6). Anything in the cart that
     * wasn't part of this checkout attempt (excluded by {@code selectedVariantIds}, or dropped by
     * this final revalidation) stays in the cart untouched.
     *
     * @param userUuid           the caller's Keycloak UUID
     * @param address            the shipping address to snapshot onto the order
     * @param selectedVariantIds optional subset of variant ids to restrict this checkout to;
     *                           {@code null} checks out the whole cart
     * @return the created order plus any lines dropped at this final revalidation
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the cart (or the
     *         selected subset of it) is empty, or every line in it is currently unavailable
     */
    CheckoutResult confirm(String userUuid, CheckoutCommands.AddressInput address, List<Integer> selectedVariantIds);
}
