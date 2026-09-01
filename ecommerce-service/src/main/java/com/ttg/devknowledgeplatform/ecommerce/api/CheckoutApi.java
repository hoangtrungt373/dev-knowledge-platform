package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.ecommerce.dto.AddressRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.CheckoutConfirmResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CheckoutPreviewResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * HTTP contract for checkout (Epic 2, US-2.5–2.7). Authenticated-only, same as {@code CartApi} —
 * falls under this module's {@code security/SecurityConfig}'s default
 * {@code anyRequest().authenticated()} rule, no new rule needed.
 *
 * <p>Two-step flow: {@link #preview} for the shopper to review before committing to anything,
 * {@link #confirm} to actually place the order. See {@code CheckoutService}'s own Javadoc for why
 * {@code confirm} re-validates fresh rather than trusting a client-cached preview.
 */
@RequestMapping("/api/v1/checkout")
public interface CheckoutApi {

    /**
     * Revalidates the caller's cart and returns what confirming it right now would produce
     * (US-2.6).
     *
     * @param userUuid           the caller's Keycloak UUID
     * @param selectedVariantIds optional subset of variant ids to restrict this preview to
     *                           (repeated query param, e.g. {@code ?selectedVariantIds=1&selectedVariantIds=2});
     *                           omitted previews the whole cart
     * @param subtotalCouponCode optional coupon code targeting the subtotal (Coupon feature,
     *                           Phase 2); omitted/blank applies none
     * @param shippingCouponCode optional coupon code targeting the shipping fee; omitted/blank
     *                           applies none
     * @return {@code 200} with the reviewable preview
     */
    @GetMapping("/preview")
    ResponseEntity<CheckoutPreviewResponse> preview(
            @CurrentUserId String userUuid,
            @RequestParam(required = false) List<Integer> selectedVariantIds,
            @RequestParam(required = false) String subtotalCouponCode,
            @RequestParam(required = false) String shippingCouponCode);

    /**
     * Creates an order from the caller's currently-available cart lines and the given shipping
     * address (US-2.5, US-2.6), clearing the cart on success. {@code request} carries either an
     * existing AddressBook entry ({@code savedAddressId}) or a fresh, one-off address — see
     * {@code AddressRequest}'s own Javadoc for the full two-shape contract, including the optional
     * "save this for next time" flag.
     *
     * @param userUuid the caller's Keycloak UUID
     * @param request  the shipping address to snapshot onto the order
     * @return {@code 201} with the created order
     */
    @PostMapping("/confirm")
    ResponseEntity<CheckoutConfirmResponse> confirm(@CurrentUserId String userUuid, @Valid @RequestBody AddressRequest request);
}
