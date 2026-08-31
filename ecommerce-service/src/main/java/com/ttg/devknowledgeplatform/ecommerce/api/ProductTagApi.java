package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductTagRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductTagResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductTagRequest;

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
 * HTTP contract for the admin product-tag management API.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.ProductTagController})
 * carries no HTTP annotations, matching {@code content-service}'s {@code TagApi}/{@code TagController} split
 * — this interface mirrors that one almost method-for-method, minus the {@code status} filter
 * (see {@code ProductTag}'s own Javadoc for why this entity has no status field).
 */
@RequestMapping("/api/v1/admin/product-tags")
public interface ProductTagApi {

    /**
     * Creates a new product tag.
     *
     * @param request validated creation payload
     * @return {@code 201} with the created tag
     */
    @PostMapping
    ResponseEntity<ProductTagResponse> create(@Valid @RequestBody CreateProductTagRequest request);

    /**
     * Renames an existing product tag.
     *
     * @param id      tag primary key
     * @param request validated update payload
     * @return {@code 200} with the updated tag
     */
    @PutMapping("/{id}")
    ResponseEntity<ProductTagResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateProductTagRequest request);

    /**
     * Deletes a product tag by its primary key. Rejected while the tag is still assigned to any product.
     *
     * @param id tag primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /**
     * Returns a single product tag by its primary key.
     *
     * @param id tag primary key
     * @return {@code 200} with the tag
     */
    @GetMapping("/{id}")
    ResponseEntity<ProductTagResponse> getById(@PathVariable Integer id);

    /**
     * Returns a paginated, optionally filtered list of product tags.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code name}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param q       optional case-insensitive name/slug substring filter
     * @return {@code 200} with a paged list of tags
     */
    @GetMapping
    ResponseEntity<PagedResponse<ProductTagResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String q);
}
