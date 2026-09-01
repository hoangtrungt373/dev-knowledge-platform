package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/**
 * REST response shape for an image uploaded for a {@code Coupon} — a permanent, unsigned URL
 * (never expires), unlike {@code ProductImageResponse.url}'s time-limited presigned URL. See
 * {@code CouponImageService}'s Javadoc for why.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CouponImageResponse {

    private String url;
}
