package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductAttributeRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductAttributeResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductAttributeRequest;

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
 * HTTP contract for the admin product-attribute management API — the "Option B" global attribute
 * registry (e.g. "Color", with a controlled vocabulary of values), assigned to categories via
 * {@code ProductCategoryApi}'s own {@code attributes} field on create/update.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.ProductAttributeController})
 * carries no HTTP annotations, mirroring {@code ProductTagApi}/{@code ProductTagController}'s split.
 */
@RequestMapping("/api/v1/admin/product-attributes")
public interface ProductAttributeApi {

    /**
     * Creates a new product attribute with its full value list.
     *
     * @param request validated creation payload
     * @return {@code 201} with the created attribute
     */
    @PostMapping
    ResponseEntity<ProductAttributeResponse> create(@Valid @RequestBody CreateProductAttributeRequest request);

    /**
     * Renames an existing product attribute and/or replaces its value list.
     *
     * @param id      attribute primary key
     * @param request validated update payload
     * @return {@code 200} with the updated attribute
     */
    @PutMapping("/{id}")
    ResponseEntity<ProductAttributeResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateProductAttributeRequest request);

    /**
     * Deletes a product attribute by its primary key. Rejected while the attribute is still
     * assigned to any category.
     *
     * @param id attribute primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /**
     * Returns a single product attribute by its primary key.
     *
     * @param id attribute primary key
     * @return {@code 200} with the attribute
     */
    @GetMapping("/{id}")
    ResponseEntity<ProductAttributeResponse> getById(@PathVariable Integer id);

    /**
     * Returns a paginated, optionally filtered list of product attributes.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code name}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param q       optional case-insensitive name substring filter
     * @return {@code 200} with a paged list of attributes
     */
    @GetMapping
    ResponseEntity<PagedResponse<ProductAttributeResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String q);
}
