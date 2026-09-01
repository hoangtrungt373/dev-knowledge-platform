package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.ecommerce.dto.CouponImageResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP contract for uploading a {@code Coupon}'s promo banner/icon image — a separate resource
 * from {@link CouponApi} since this touches no existing {@code Coupon}/{@code couponId} at all
 * (see {@code CouponImageService}'s Javadoc), mirroring {@link ProductDescriptionImageApi}'s own
 * split from {@link ProductApi} for the identical reason. Nested under
 * {@code /api/v1/admin/coupons} purely so `gateway`'s existing {@code /api/v1/admin/coupons/**}
 * route already covers it with no routing change needed — not because this is part of
 * {@link CouponApi}'s own resource.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.CouponImageController})
 * carries no HTTP annotations, matching this module's usual {@code api}/{@code api.impl} split.
 */
@RequestMapping("/api/v1/admin/coupons/images")
public interface CouponImageApi {

    /**
     * Uploads an image and returns a permanent URL for it — usable in create mode too (unlike a
     * hypothetical id-scoped upload, this needs no existing coupon to attach to).
     *
     * @param file the image file
     * @return {@code 201} with the permanent URL
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CouponImageResponse> upload(@RequestParam("file") MultipartFile file);
}
