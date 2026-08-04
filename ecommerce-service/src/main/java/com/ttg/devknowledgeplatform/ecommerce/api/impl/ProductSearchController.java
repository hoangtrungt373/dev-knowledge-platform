package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.ProductSearchApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductSearchResponse;
import com.ttg.devknowledgeplatform.ecommerce.mapper.ProductSearchViewMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductSearchService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Implementation of {@link ProductSearchApi}.
 */
@RestController
@RequiredArgsConstructor
public class ProductSearchController implements ProductSearchApi {

    private final ProductSearchService productSearchService;
    private final ProductSearchViewMapper productSearchViewMapper;

    @Override
    public ResponseEntity<PagedResponse<ProductSearchResponse>> search(
            int page, int size, Integer categoryId, String q,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly) {
        Page<ProductSearchResponse> responses = productSearchService
                .search(page, size, categoryId, q, minPrice, maxPrice, inStockOnly)
                .map(productSearchViewMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }
}
