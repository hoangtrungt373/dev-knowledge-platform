package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The CQRS read model for catalog browsing/search — one denormalized row per {@link Product},
 * written only by the outbox-driven projection relay (never directly by request-handling code),
 * and the only table the browse/search/filter endpoints query.
 *
 * <p>{@link #categoryName} is denormalized (copied from {@code ProductCategory.name} at
 * projection time) specifically to avoid a join on every catalog read — the whole point of this
 * table. {@link #searchText} backs {@code pg_trgm} typo-tolerant matching; the DB additionally
 * maintains a generated, GIN-indexed {@code tsvector} column derived from the same text (see the
 * Liquibase migration) — that column isn't mapped here at all, since Postgres computes it itself
 * ({@code GENERATED ALWAYS AS ... STORED}) and this entity never needs to read or write it
 * directly, only reference it in native search queries.
 *
 * <p>{@link #availableAttributes} answers "which attribute values does this product offer" for
 * filtering (US-1.4), e.g. {@code {"size": ["S","M"], "color": ["Blue"]}}. Filtering by
 * {@code size=M AND color=Blue} checks these independently (some variant has size M, some variant
 * has color Blue) — not that a single variant has both together. Good enough for typical
 * checkbox-filter UX; a combo-accurate filter would need a separate per-variant projection.
 *
 * <p>A brand-new product has no row here until the projection relay processes its
 * {@code PRODUCT_CHANGED} outbox event ({@link OutboxEvent}) — an intentional, bounded staleness
 * window (US-1.5), not a bug.
 */
@Entity
@Table(name = "PRODUCT_SEARCH_VIEW", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_SEARCH_VIEW_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"product"})
@ToString(exclude = {"product"})
public class ProductSearchView extends AbstractEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false, unique = true)
    private Product product;

    @Column(name = "NAME", length = 150, nullable = false)
    private String name;

    @Column(name = "SLUG", length = 150, nullable = false)
    private String slug;

    // Plain denormalized columns, not a @ManyToOne — a read model has no business joining back
    // to ProductCategory on every query, which is exactly what an association mapping here would
    // tempt someone into doing later.
    @Column(name = "PRODUCT_CATEGORY_ID", nullable = false)
    private Integer productCategoryId;

    @Column(name = "CATEGORY_NAME", length = 100, nullable = false)
    private String categoryName;

    @Column(name = "MIN_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "MAX_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "IN_STOCK", nullable = false)
    private boolean inStock;

    @Column(name = "SEARCH_TEXT", columnDefinition = "TEXT", nullable = false)
    private String searchText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "AVAILABLE_ATTRIBUTES", columnDefinition = "JSONB", nullable = false)
    private Map<String, List<String>> availableAttributes;
}
