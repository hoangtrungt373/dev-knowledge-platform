package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Manages the lifecycle of {@link Coupon}s — Phase 1 ("ProductDiscount" feature) of admin CRUD
 * only. Coupon *redemption* (validating a shopper's entered code against a cart and applying its
 * discount) doesn't live here yet — that's Phase 2's {@code CouponRedemptionService}/equivalent,
 * wired into {@code CheckoutServiceImpl} once it exists.
 *
 * <p>Returns entities rather than REST DTOs — {@code mapper.CouponMapper} does the
 * entity-to-response mapping, matching every other admin CRUD service in this module.
 */
public interface CouponService {

    /**
     * Creates a new coupon.
     *
     * @param command creation fields
     * @return the created coupon
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the code is already
     *         taken, the value is invalid for the given type, or the date range is invalid
     */
    Coupon create(CouponCommands.Create command);

    /**
     * Updates an existing coupon's fields — everything except {@link Coupon#getCode()}, which is
     * immutable after creation (see that entity's own Javadoc).
     *
     * @param id      the coupon's primary key
     * @param command updated fields
     * @return the updated coupon
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code id} does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the value is invalid for the given type
     *         or the date range is invalid
     */
    Coupon update(Integer id, CouponCommands.Update command);

    /**
     * Returns a single coupon by its primary key.
     *
     * @param id the coupon's primary key
     * @return the matching coupon
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    Coupon getById(Integer id);

    /**
     * Returns a paginated, optionally filtered list of coupons.
     *
     * @param pageable pagination and sort parameters
     * @param q        case-insensitive code substring filter; {@code null}/blank returns all
     * @param active   optional active/inactive filter; {@code null} returns both
     * @param target   optional target filter; {@code null} returns both
     * @return a page of matching coupons
     */
    Page<Coupon> list(Pageable pageable, String q, Boolean active, CouponTarget target);

    /**
     * Permanently deletes a coupon.
     *
     * @param id the coupon's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the coupon has ever
     *         been redeemed
     */
    void delete(Integer id);
}
