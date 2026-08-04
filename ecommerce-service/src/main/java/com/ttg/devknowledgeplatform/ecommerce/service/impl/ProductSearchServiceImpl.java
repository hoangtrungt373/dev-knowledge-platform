package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductSearchViewRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductSearchService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSearchServiceImpl implements ProductSearchService {

    /**
     * {@code pg_trgm} similarity floor for typo-tolerant matches — below this, results are too
     * loosely related to the query to be useful. A tunable {@code @ConfigurationProperties}
     * field would be the natural next step if this ever needs adjusting per environment.
     */
    private static final double TRIGRAM_THRESHOLD = 0.3;

    private final ProductSearchViewRepository productSearchViewRepository;

    @Override
    public Page<ProductSearchView> search(
            int page, int size, Integer categoryId, String q,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly) {
        // Unsorted on purpose: the native query bakes in its own ORDER BY (relevance when q is
        // present, recency otherwise) — a Sort here would make Spring Data append a second,
        // conflicting ORDER BY clause.
        return productSearchViewRepository.search(
                categoryId, blankToNull(q), minPrice, maxPrice, inStockOnly, TRIGRAM_THRESHOLD,
                PageRequest.of(page, size));
    }

    private static String blankToNull(String q) {
        return (q == null || q.isBlank()) ? null : q;
    }
}
