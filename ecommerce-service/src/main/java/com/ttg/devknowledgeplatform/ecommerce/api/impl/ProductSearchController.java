package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.ProductSearchApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductSearchResponse;
import com.ttg.devknowledgeplatform.ecommerce.mapper.ProductMapper;
import com.ttg.devknowledgeplatform.ecommerce.mapper.ProductSearchViewMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductSearchService;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link ProductSearchApi}.
 */
@RestController
@RequiredArgsConstructor
public class ProductSearchController implements ProductSearchApi {

    /** Every named search param — anything else in the query string is treated as an attribute filter. */
    private static final Set<String> RESERVED_SEARCH_PARAMS =
            Set.of("page", "size", "categoryId", "q", "minPrice", "maxPrice", "inStockOnly");

    private final ProductSearchService productSearchService;
    private final ProductSearchViewMapper productSearchViewMapper;
    private final ProductService productService;
    private final ProductMapper productMapper;

    @Override
    public ResponseEntity<PagedResponse<ProductSearchResponse>> search(
            int page, int size, Integer categoryId, String q,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly,
            Map<String, String> allParams) {
        Map<String, String> attributeFilters = new HashMap<>(allParams);
        attributeFilters.keySet().removeAll(RESERVED_SEARCH_PARAMS);

        Page<ProductSearchResponse> responses = productSearchService
                .search(page, size, categoryId, q, minPrice, maxPrice, inStockOnly, attributeFilters)
                .map(productSearchViewMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    @Override
    public ResponseEntity<ProductResponse> getBySlug(String slug) {
        return ResponseEntity.ok(productMapper.toResponse(productService.getActiveBySlug(slug)));
    }
}
