package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CouponResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateCouponRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateCouponRequest;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the admin coupon management API — Phase 1 ("ProductDiscount" feature, admin
 * CRUD only; redemption at checkout is Phase 2, not exposed here).
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.CouponController})
 * carries no HTTP annotations, matching every other {@code *Api}/{@code *Controller} split in this
 * module.
 */
@RequestMapping("/api/v1/admin/coupons")
public interface CouponApi {

    /**
     * Creates a new coupon.
     *
     * @param request validated creation payload
     * @return {@code 201} with the created coupon
     */
    @PostMapping
    ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request);

    /**
     * Updates an existing coupon's fields (everything but its code).
     *
     * @param id      coupon primary key
     * @param request validated update payload
     * @return {@code 200} with the updated coupon
     */
    @PutMapping("/{id}")
    ResponseEntity<CouponResponse> update(@PathVariable Integer id, @Valid @RequestBody UpdateCouponRequest request);

    /**
     * Deletes a coupon by its primary key. Rejected once the coupon has ever been redeemed.
     *
     * @param id coupon primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /**
     * Returns a single coupon by its primary key.
     *
     * @param id coupon primary key
     * @return {@code 200} with the coupon
     */
    @GetMapping("/{id}")
    ResponseEntity<CouponResponse> getById(@PathVariable Integer id);

    /**
     * Returns a paginated, optionally filtered list of coupons.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code code}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param q       optional case-insensitive code substring filter
     * @param active  optional active/inactive filter
     * @param target  optional target filter
     * @return {@code 200} with a paged list of coupons
     */
    @GetMapping
    ResponseEntity<PagedResponse<CouponResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) CouponTarget target);
}
