package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductImageRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductImageResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductVariantRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductVariantResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductImageSortOrderRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductRequest;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * HTTP contract for the admin product management API.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.ProductController})
 * carries no HTTP annotations, matching {@code content-service}'s {@code CategoryApi}/{@code CategoryController} split.
 */
@RequestMapping("/api/v1/admin/products")
public interface ProductApi {

    /**
     * Creates a product together with its variants and image gallery.
     *
     * @param request validated creation payload, including at least one variant
     * @return {@code 201} with the created product
     */
    @PostMapping
    ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request);

    /**
     * Updates a product's basic fields. Does not touch variants or images — see the
     * variant/image endpoints below (US-1.6).
     *
     * @param id      product primary key
     * @param request validated update payload
     * @return {@code 200} with the updated product
     */
    @PutMapping("/{id}")
    ResponseEntity<ProductResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateProductRequest request);

    /**
     * Adds one variant to an existing product (US-1.6).
     *
     * @param id      product primary key
     * @param request validated variant payload
     * @return {@code 201} with the added variant
     */
    @PostMapping("/{id}/variants")
    ResponseEntity<ProductVariantResponse> addVariant(
            @PathVariable Integer id, @Valid @RequestBody ProductVariantRequest request);

    /**
     * Removes one variant from a product (US-1.6). Rejected if it's the product's last variant.
     *
     * @param id        product primary key
     * @param variantId variant primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}/variants/{variantId}")
    ResponseEntity<Void> removeVariant(@PathVariable Integer id, @PathVariable Integer variantId);

    /**
     * Adds one image to a product's gallery (US-1.6).
     *
     * @param id      product primary key
     * @param request validated image payload
     * @return {@code 201} with the added image
     */
    @PostMapping("/{id}/images")
    ResponseEntity<ProductImageResponse> addImage(
            @PathVariable Integer id, @Valid @RequestBody ProductImageRequest request);

    /**
     * Uploads an image file and adds it to a product's gallery (US-1.6) — the real upload path
     * for the admin GUI. {@link #addImage} still exists for a caller that already knows a
     * {@code storageKey}; this is the endpoint that actually writes bytes to MinIO, via
     * {@code infra}'s {@code StorageService}.
     *
     * @param id        product primary key
     * @param file      the image file ({@code StorageService} rejects a non-image content type or
     *                  a file over 5 MB)
     * @param sortOrder the new image's position in the gallery
     * @return {@code 201} with the added image, including a time-limited presigned {@code url}
     */
    @PostMapping(path = "/{id}/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ProductImageResponse> uploadImage(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("sortOrder") Integer sortOrder);

    /**
     * Removes one image from a product's gallery (US-1.6).
     *
     * @param id      product primary key
     * @param imageId image primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}/images/{imageId}")
    ResponseEntity<Void> removeImage(@PathVariable Integer id, @PathVariable Integer imageId);

    /**
     * Changes one image's position in the gallery (US-1.6).
     *
     * @param id      product primary key
     * @param imageId image primary key
     * @param request validated new sort order
     * @return {@code 200} with the updated image
     */
    @PatchMapping("/{id}/images/{imageId}")
    ResponseEntity<ProductImageResponse> updateImageSortOrder(
            @PathVariable Integer id, @PathVariable Integer imageId,
            @Valid @RequestBody UpdateProductImageSortOrderRequest request);

    /**
     * Soft-deletes a product by marking it inactive (US-1.7).
     *
     * @param id product primary key
     * @return {@code 200} with the deactivated product
     */
    @PatchMapping("/{id}/deactivate")
    ResponseEntity<ProductResponse> deactivate(@PathVariable Integer id);

    /**
     * Returns a single product, including its variants and image gallery, by its primary key.
     *
     * @param id product primary key
     * @return {@code 200} with the product
     */
    @GetMapping("/{id}")
    ResponseEntity<ProductResponse> getById(@PathVariable Integer id);

    /**
     * Paginated, optionally filtered list of products.
     *
     * @param page              zero-based page number (default 0)
     * @param size              page size (default 20)
     * @param sortBy            field to sort by; allowed values: {@code id}, {@code name}, {@code dteCreation} (default {@code id})
     * @param sortDir           sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param productCategoryId optional category filter
     * @param active            optional active-flag filter
     * @param q                 optional case-insensitive name/slug substring filter
     * @param tagIds            optional tag filter — matches a product tagged with *any* of the given ids
     * @return {@code 200} with a paged list of products
     */
    @GetMapping
    ResponseEntity<PagedResponse<ProductResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Integer productCategoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Set<Integer> tagIds);
}
