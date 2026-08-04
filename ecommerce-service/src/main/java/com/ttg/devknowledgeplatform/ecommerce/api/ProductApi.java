package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
     * Updates a product's basic fields. Does not touch variants or images.
     *
     * @param id      product primary key
     * @param request validated update payload
     * @return {@code 200} with the updated product
     */
    @PutMapping("/{id}")
    ResponseEntity<ProductResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateProductRequest request);

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
            @RequestParam(required = false) String q);
}
