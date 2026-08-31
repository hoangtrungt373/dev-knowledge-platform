package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductDescriptionImageResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP contract for uploading an image to embed inline in a {@code Product.description} — a
 * separate resource from {@link ProductApi}'s own gallery-image upload since this touches no
 * {@code Product}/{@code productId} at all (see {@code ProductDescriptionImageService}'s Javadoc).
 * Nested under {@code /api/v1/admin/products} purely so `gateway`'s existing
 * {@code /api/v1/admin/products/**} route already covers it with no routing change needed — not
 * because this is part of {@link ProductApi}'s own resource.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.ProductDescriptionImageController})
 * carries no HTTP annotations, matching this module's usual {@code api}/{@code api.impl} split.
 */
@RequestMapping("/api/v1/admin/products/description-images")
public interface ProductDescriptionImageApi {

    /**
     * Uploads an image and returns a permanent URL for it — usable in create mode too (unlike
     * {@link ProductApi#uploadImage}, this needs no existing product to attach to).
     *
     * @param file the image file
     * @return {@code 201} with the permanent URL
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ProductDescriptionImageResponse> upload(@RequestParam("file") MultipartFile file);
}
