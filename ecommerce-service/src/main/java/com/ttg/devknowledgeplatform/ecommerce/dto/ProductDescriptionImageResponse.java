package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/**
 * REST response shape for an image uploaded to embed inline in a {@code Product.description} —
 * a permanent, unsigned URL (never expires), unlike {@code ProductImageResponse.url}'s time-limited
 * presigned URL. See {@code ProductDescriptionImageService}'s Javadoc for why the two are different.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDescriptionImageResponse {

    private String url;
}
