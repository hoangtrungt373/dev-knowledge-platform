package com.ttg.devknowledgeplatform.ecommerce.service;

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
 */
public interface CheckoutService {

    /**
     * Revalidates the caller's cart and computes what confirming it right now would produce.
     *
     * @param userUuid the caller's Keycloak UUID
     * @return the reviewable preview
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the cart is empty, or
     *         every line in it is currently unavailable
     */
    CheckoutPreview preview(String userUuid);

    /**
     * Creates an {@code Order} from the caller's currently-available cart lines and the given
     * shipping address, then clears the cart — only once the order has been saved successfully,
     * never before (US-2.6).
     *
     * @param userUuid the caller's Keycloak UUID
     * @param address  the shipping address to snapshot onto the order
     * @return the created order plus any lines dropped at this final revalidation
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the cart is empty, or
     *         every line in it is currently unavailable
     */
    CheckoutResult confirm(String userUuid, CheckoutCommands.AddressInput address);
}
