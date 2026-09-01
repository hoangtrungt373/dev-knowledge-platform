package com.ttg.devknowledgeplatform.ecommerce.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Uploads a promo banner/icon image for a {@link com.ttg.devknowledgeplatform.ecommerce.entity.Coupon}
 * and returns a permanent URL for it — deliberately not part of {@link CouponService}, mirroring
 * {@code ProductDescriptionImageService}'s own split from {@code ProductService}: this touches no
 * existing {@code Coupon} row (no {@code couponId}), so it works in create mode too, and the image
 * is set as a plain field on the create/update request afterward rather than resolved by an id.
 *
 * <p>Uses {@code infra}'s {@code StorageService.uploadPublicImage} — same choice
 * {@code ProductDescriptionImageService} made, for the same reason: a {@code Coupon} has no
 * "not-yet-published/deactivated" access-control concern the way a {@code Product}'s own gallery
 * does (see {@code StorageService}'s own Javadoc), so there's no reason to pay for a presigned
 * URL's re-signing-on-every-read cost here.
 */
public interface CouponImageService {

    /**
     * Validates and uploads {@code file}, returning a permanent, never-expiring URL.
     *
     * @param file the multipart file from the HTTP request
     * @return the permanent URL
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the file isn't an image or exceeds the size limit
     */
    String upload(MultipartFile file);
}
