package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductSearchViewRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductSearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductSearchServiceImpl implements ProductSearchService {

    /**
     * {@code pg_trgm} similarity floor for typo-tolerant matches — below this, results are too
     * loosely related to the query to be useful. A tunable {@code @ConfigurationProperties}
     * field would be the natural next step if this ever needs adjusting per environment.
     */
    private static final double TRIGRAM_THRESHOLD = 0.3;

    private final ProductSearchViewRepository productSearchViewRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Page<ProductSearchView> search(
            int page, int size, Integer categoryId, String q,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly,
            Map<String, String> attributeFilters) {
        // Unsorted on purpose: the native query bakes in its own ORDER BY (relevance when q is
        // present, recency otherwise) — a Sort here would make Spring Data append a second,
        // conflicting ORDER BY clause.
        return productSearchViewRepository.search(
                categoryId, blankToNull(q), minPrice, maxPrice, inStockOnly,
                toAttributesFilterJson(attributeFilters), TRIGRAM_THRESHOLD,
                PageRequest.of(page, size));
    }

    /**
     * Combines every {@code key=value} attribute filter into one JSON object (each value wrapped
     * in a singleton array, matching {@code availableAttributes}' shape) for a single JSONB
     * containment bind parameter — see {@code ProductSearchViewRepository.search}'s Javadoc for
     * why one combined parameter, not one condition per filter.
     */
    private String toAttributesFilterJson(Map<String, String> attributeFilters) {
        if (attributeFilters == null || attributeFilters.isEmpty()) {
            return null;
        }
        Map<String, List<String>> filterAsLists = new LinkedHashMap<>();
        attributeFilters.forEach((key, value) -> filterAsLists.put(key, List.of(value)));
        try {
            return objectMapper.writeValueAsString(filterAsLists);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize attribute filters {}, ignoring them: {}", attributeFilters, e.getMessage());
            return null;
        }
    }

    private static String blankToNull(String q) {
        return (q == null || q.isBlank()) ? null : q;
    }
}
