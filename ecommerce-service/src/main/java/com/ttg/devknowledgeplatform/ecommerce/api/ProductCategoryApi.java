package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductCategoryRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductCategoryResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductCategoryRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * HTTP contract for the admin product-category management API.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.ProductCategoryController})
 * carries no HTTP annotations, matching {@code content-service}'s {@code CategoryApi}/{@code CategoryController} split.
 */
@RequestMapping("/api/v1/admin/product-categories")
public interface ProductCategoryApi {

    /**
     * Creates a new product category.
     *
     * @param request validated creation payload
     * @return {@code 201} with the created category
     */
    @PostMapping
    ResponseEntity<ProductCategoryResponse> create(@Valid @RequestBody CreateProductCategoryRequest request);

    /**
     * Renames an existing product category.
     *
     * @param id      category primary key
     * @param request validated update payload
     * @return {@code 200} with the updated category
     */
    @PutMapping("/{id}")
    ResponseEntity<ProductCategoryResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateProductCategoryRequest request);

    /**
     * Returns a single product category by its primary key.
     *
     * @param id category primary key
     * @return {@code 200} with the category
     */
    @GetMapping("/{id}")
    ResponseEntity<ProductCategoryResponse> getById(@PathVariable Integer id);

    /**
     * Flat, unpaginated list, sorted by name — this taxonomy is expected to stay small.
     *
     * @param q optional case-insensitive name/slug substring filter
     * @return {@code 200} with matching categories
     */
    @GetMapping
    ResponseEntity<List<ProductCategoryResponse>> list(@RequestParam(required = false) String q);
}
