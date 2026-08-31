package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.ecommerce.dto.AddCartItemRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.CartResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.RemoveCartItemsRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateCartItemRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for the shopper's cart (Epic 2, US-2.1–2.4). Authenticated-only — no guest cart,
 * no merge-on-login (see {@code docs/user-stories/02-cart-checkout.md}'s locked decisions) — so
 * every method here resolves the caller via {@code @CurrentUserId}, and the whole
 * {@code /api/v1/cart/**} path falls under this module's {@code security/SecurityConfig}'s
 * default {@code anyRequest().authenticated()} rule (no {@code /public/**}/{@code /admin/**}
 * prefix to opt into a different rule).
 *
 * <p>Every mutating method returns the updated {@link CartResponse}, not just {@code 204}/the
 * mutated line — the caller (typically a GUI cart drawer) always wants the freshly-resolved cart
 * back, and a second {@code GET} round trip after every mutation would be wasteful.
 */
@RequestMapping("/api/v1/cart")
public interface CartApi {

    /**
     * Returns the caller's cart, every line resolved live against the current catalog (US-2.3).
     *
     * @param userUuid the caller's Keycloak UUID
     * @return {@code 200} with the resolved cart
     */
    @GetMapping
    ResponseEntity<CartResponse> getCart(@CurrentUserId String userUuid);

    /**
     * Adds a variant to the cart, incrementing its quantity if already present (US-2.1).
     *
     * @param userUuid the caller's Keycloak UUID
     * @param request  the variant and quantity to add
     * @return {@code 200} with the updated cart
     */
    @PostMapping("/items")
    ResponseEntity<CartResponse> addItem(@CurrentUserId String userUuid, @Valid @RequestBody AddCartItemRequest request);

    /**
     * Sets a line's quantity to an absolute value; {@code 0} removes it entirely (US-2.2).
     *
     * @param userUuid  the caller's Keycloak UUID
     * @param variantId the line to update
     * @param request   the new absolute quantity
     * @return {@code 200} with the updated cart
     */
    @PutMapping("/items/{variantId}")
    ResponseEntity<CartResponse> updateItem(
            @CurrentUserId String userUuid, @PathVariable Integer variantId, @Valid @RequestBody UpdateCartItemRequest request);

    /**
     * Removes a line entirely — equivalent to {@link #updateItem} with quantity {@code 0}, offered
     * as its own endpoint since "remove" is a distinct shopper action from "change quantity."
     *
     * @param userUuid  the caller's Keycloak UUID
     * @param variantId the line to remove
     * @return {@code 200} with the updated cart
     */
    @DeleteMapping("/items/{variantId}")
    ResponseEntity<CartResponse> removeItem(@CurrentUserId String userUuid, @PathVariable Integer variantId);

    /**
     * Removes multiple lines from the cart in one call (bulk delete) — a {@code POST} action
     * endpoint rather than {@code DELETE} with a body, since not every HTTP client/proxy layer
     * reliably forwards a body on a {@code DELETE} request.
     *
     * @param userUuid the caller's Keycloak UUID
     * @param request  the variant ids to remove
     * @return {@code 200} with the updated cart
     */
    @PostMapping("/items/remove-batch")
    ResponseEntity<CartResponse> removeItems(@CurrentUserId String userUuid, @Valid @RequestBody RemoveCartItemsRequest request);
}
