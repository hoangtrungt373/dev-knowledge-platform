package com.ttg.devknowledgeplatform.ecommerce.service;

import java.util.List;

/**
 * Manages a shopper's cart — stored in Redis (a primary store here, not a cache; see
 * {@code docs/user-stories/02-cart-checkout.md}'s "Key decisions locked for this epic"), keyed by
 * the caller's Keycloak UUID (never a guest cart — Epic 2 is authenticated-only).
 *
 * <p>Returns {@link Cart}/{@link CartLine} (this module's own plain domain records, not entities
 * — there's no JPA-mapped table backing a cart at all), never REST DTOs — {@code CartMapper} does
 * that translation, same split every other service in this module follows.
 */
public interface CartService {

    /**
     * Adds a variant to the cart, incrementing its quantity if already present (US-2.1) — never
     * creates a duplicate line for the same variant.
     *
     * @param userUuid  the caller's Keycloak UUID
     * @param variantId the variant to add
     * @param quantity  how many to add (must be positive — validated at the request layer)
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the variant does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the variant's product is inactive
     */
    void addItem(String userUuid, Integer variantId, int quantity);

    /**
     * Sets a line's quantity to an absolute value, or removes it entirely when {@code quantity}
     * is {@code 0} (US-2.2). Unlike {@link #addItem}, does not validate variant availability when
     * removing — a shopper must always be able to remove a now-invalid line.
     *
     * @param userUuid  the caller's Keycloak UUID
     * @param variantId the line to update
     * @param quantity  the new absolute quantity; {@code 0} removes the line
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if increasing a nonexistent
     *         variant's quantity (not applicable when removing)
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if increasing an inactive variant's quantity
     */
    void setQuantity(String userUuid, Integer variantId, int quantity);

    /**
     * Returns the caller's cart, every line resolved live against the current catalog (US-2.3) —
     * price, availability, and product/variant details are never snapshotted for a cart (contrast
     * with {@code Order}, which does snapshot at creation — see Epic 3).
     *
     * @param userUuid the caller's Keycloak UUID
     * @return the resolved cart (empty line list if the cart is empty or expired)
     */
    Cart getCart(String userUuid);

    /**
     * Removes multiple lines from the cart in one call (bulk delete) — same semantics as calling
     * {@link #setQuantity} with {@code quantity} {@code 0} for each id individually, but as a
     * single Redis {@code HDEL} rather than N round trips. No validation on the ids being
     * removed, same reasoning as {@link #setQuantity}'s removal branch: a shopper must always be
     * able to remove an already-invalid line.
     *
     * @param userUuid   the caller's Keycloak UUID
     * @param variantIds the lines to remove; an id not currently in the cart is silently ignored
     */
    void removeItems(String userUuid, List<Integer> variantIds);
}
