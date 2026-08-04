package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ProductSearchViewRepository extends JpaRepository<ProductSearchView, Integer> {

    Optional<ProductSearchView> findByProductId(Integer productId);

    void deleteByProductId(Integer productId);

    /**
     * Keyword/filter search over the CQRS read model — the only query the public browse/search
     * endpoint runs.
     *
     * <p>Every filter uses the {@code (:param IS NULL OR ...)} idiom so one static query handles
     * every combination of optional filters, rather than building the SQL dynamically.
     * {@code q} matches via {@code tsvector} exact-token search ({@code @@}) OR {@code pg_trgm}
     * similarity (catches typos {@code tsvector} alone would miss); results are ranked by
     * {@code ts_rank} when {@code q} is present, otherwise by recency. The price filter is an
     * overlap check ({@code MAX_PRICE >= :minPrice AND MIN_PRICE <= :maxPrice}), not an exact
     * match — a product with variants spanning the requested range matches even if not every
     * variant falls inside it (US-1.4).
     *
     * <p>{@code SEARCH_VECTOR} (a DB-generated column, not mapped on the entity — see
     * {@code ProductSearchView}'s Javadoc) is referenced here only inside the query text, never
     * selected — the {@code SELECT} list is spelled out explicitly (not {@code SELECT *}) so this
     * unmapped column is never returned to the entity-result mapping.
     */
    @Query(
            value = "SELECT v.PRODUCT_SEARCH_VIEW_ID, v.PRODUCT_ID, v.NAME, v.SLUG, v.PRODUCT_CATEGORY_ID, "
                    + "v.CATEGORY_NAME, v.MIN_PRICE, v.MAX_PRICE, v.IN_STOCK, v.SEARCH_TEXT, v.AVAILABLE_ATTRIBUTES, "
                    + "v.USR_CREATION, v.DTE_CREATION, v.USR_LAST_MODIFICATION, v.DTE_LAST_MODIFICATION, v.VERSION "
                    + "FROM product.PRODUCT_SEARCH_VIEW v "
                    + "WHERE (:categoryId IS NULL OR v.PRODUCT_CATEGORY_ID = :categoryId) "
                    + "AND (:inStockOnly = FALSE OR v.IN_STOCK = TRUE) "
                    + "AND (:minPrice IS NULL OR v.MAX_PRICE >= :minPrice) "
                    + "AND (:maxPrice IS NULL OR v.MIN_PRICE <= :maxPrice) "
                    + "AND (:q IS NULL "
                    + "     OR v.SEARCH_VECTOR @@ plainto_tsquery('english', :q) "
                    + "     OR similarity(v.SEARCH_TEXT, :q) > :trigramThreshold) "
                    + "ORDER BY "
                    + "  CASE WHEN :q IS NULL THEN 0 ELSE ts_rank(v.SEARCH_VECTOR, plainto_tsquery('english', :q)) END DESC, "
                    + "  v.PRODUCT_SEARCH_VIEW_ID DESC",
            countQuery = "SELECT count(*) FROM product.PRODUCT_SEARCH_VIEW v "
                    + "WHERE (:categoryId IS NULL OR v.PRODUCT_CATEGORY_ID = :categoryId) "
                    + "AND (:inStockOnly = FALSE OR v.IN_STOCK = TRUE) "
                    + "AND (:minPrice IS NULL OR v.MAX_PRICE >= :minPrice) "
                    + "AND (:maxPrice IS NULL OR v.MIN_PRICE <= :maxPrice) "
                    + "AND (:q IS NULL "
                    + "     OR v.SEARCH_VECTOR @@ plainto_tsquery('english', :q) "
                    + "     OR similarity(v.SEARCH_TEXT, :q) > :trigramThreshold)",
            nativeQuery = true)
    Page<ProductSearchView> search(
            @Param("categoryId") Integer categoryId,
            @Param("q") String q,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("inStockOnly") boolean inStockOnly,
            @Param("trigramThreshold") double trigramThreshold,
            Pageable pageable);
}
