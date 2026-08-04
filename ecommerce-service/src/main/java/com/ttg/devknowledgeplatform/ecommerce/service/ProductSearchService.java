package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;

/**
 * Public browse/search over the CQRS read model ({@code ProductSearchView}) — see that entity's
 * Javadoc for why this, and not {@code Product} directly, backs browse/search/filter.
 */
public interface ProductSearchService {

    /**
     * Searches/browses the catalog.
     *
     * @param page              zero-based page number
     * @param size              page size
     * @param categoryId        optional category filter
     * @param q                 optional keyword; matched via full-text + trigram similarity (US-1.3)
     * @param minPrice          optional lower price bound (overlap check against a product's variant range, US-1.4)
     * @param maxPrice          optional upper price bound (overlap check against a product's variant range, US-1.4)
     * @param inStockOnly       when {@code true}, excludes products with no variant in stock
     * @return a page of matching products, ranked by relevance when {@code q} is present
     */
    Page<ProductSearchView> search(
            int page, int size, Integer categoryId, String q,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly);
}
