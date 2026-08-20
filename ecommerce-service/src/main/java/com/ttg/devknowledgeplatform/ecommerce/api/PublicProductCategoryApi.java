package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductCategoryResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * HTTP contract for the public product-category listing API — a read-only counterpart to
 * {@link ProductCategoryApi}'s admin-gated {@code /api/v1/admin/product-categories}, needed
 * because the storefront's category filter rail must render for a logged-out shopper too
 * (US-1.1), and {@code /api/v1/admin/**} requires {@code ROLE_ADMIN}.
 *
 * <p>Mapped under {@code /api/v1/public/**}, which this module's own {@code security/SecurityConfig}
 * permits without authentication. The implementation
 * ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.PublicProductCategoryController}) carries
 * no HTTP annotations, matching this module's other controllers.
 */
@RequestMapping("/api/v1/public/product-categories")
public interface PublicProductCategoryApi {

    /**
     * Flat, unpaginated list of every product category, sorted by name — same underlying data as
     * {@link ProductCategoryApi#list}, just without the admin-role/query-filter surface a
     * storefront category rail has no use for.
     *
     * @return {@code 200} with every category
     */
    @GetMapping
    ResponseEntity<List<ProductCategoryResponse>> list();
}
