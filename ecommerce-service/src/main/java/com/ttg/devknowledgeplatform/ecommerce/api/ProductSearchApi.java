package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductSearchResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

/**
 * HTTP contract for the public product browse/search/detail API (US-1.1, US-1.2, US-1.3, US-1.4).
 *
 * <p>Mapped under {@code /api/v1/public/**}, which this module's own {@code security/SecurityConfig}
 * permits without authentication. The implementation
 * ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.ProductSearchController}) carries no
 * HTTP annotations, matching this module's admin controllers.
 */
@RequestMapping("/api/v1/public/products")
public interface ProductSearchApi {

    /**
     * Browses/searches products via the CQRS read model.
     *
     * @param page        zero-based page number (default 0)
     * @param size        page size (default 20)
     * @param categoryId  optional category filter
     * @param q           optional keyword; ranked results when present
     * @param minPrice    optional lower price bound (variant-range overlap)
     * @param maxPrice    optional upper price bound (variant-range overlap)
     * @param inStockOnly when {@code true}, excludes products with no variant in stock (default {@code false})
     * @param allParams   every query param on the request, including the ones above — the
     *                    implementation treats anything not named above as an attribute filter
     *                    (e.g. {@code ?size=M&color=Blue}), since attribute keys are dynamic per
     *                    category and can't be fixed named parameters (US-1.4)
     * @return {@code 200} with a paged list of matching products
     */
    @GetMapping
    ResponseEntity<PagedResponse<ProductSearchResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam Map<String, String> allParams);

    /**
     * Returns a single active product's full detail — variants and image gallery included in one
     * response (US-1.2). An inactive or nonexistent slug both resolve to {@code 404}, so a
     * deactivated product's slug never confirms its own existence to a public caller.
     *
     * @param slug the product's URL-safe slug
     * @return {@code 200} with the product's full detail
     */
    @GetMapping("/{slug}")
    ResponseEntity<ProductResponse> getBySlug(@PathVariable String slug);
}
