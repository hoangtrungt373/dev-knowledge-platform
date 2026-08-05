package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A purchasable configuration of a {@link Product} (e.g. a specific size/color), each with its
 * own SKU, price, and stock.
 *
 * <p>{@link #attributes} is a free-form key/value map (e.g. {@code {"size": "M", "color":
 * "Blue"}}) stored as JSONB via {@code @JdbcTypeCode(SqlTypes.JSON)} — the same approach
 * {@code ai-service}'s {@code ContentEmbedding.metadata} already uses, chosen over a normalized
 * key/value child table because attribute keys are genuinely dynamic per category (a "Books"
 * product has none; a "Shirts" product has {@code size}/{@code color}), and this repo already has
 * precedent for typed JSONB columns over EAV modeling. Consistency of attribute keys across a
 * product's variants (US-1.6) is a service-layer invariant, not a DB constraint — Postgres has no
 * cross-row CHECK.
 *
 * <p>{@link #stockQuantity} and {@link #reservedQuantity} together implement the two-column
 * reservation model from {@code docs/user-stories/03-order-lifecycle-inventory.md}: available
 * stock is always {@code stockQuantity - reservedQuantity}, enforced defensively by a DB CHECK
 * constraint (see the Liquibase migration) so a bug in the reservation logic can't drive either
 * value negative or reserve more than is on hand.
 */
@Entity
@Table(name = "PRODUCT_VARIANT", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_VARIANT_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"product"})
@ToString(exclude = {"product"})
public class ProductVariant extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Column(name = "SKU", length = 64, nullable = false, unique = true)
    private String sku;

    @Column(name = "PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "STOCK_QUANTITY", nullable = false)
    private Integer stockQuantity;

    @Column(name = "RESERVED_QUANTITY", nullable = false)
    private Integer reservedQuantity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ATTRIBUTES", columnDefinition = "JSONB", nullable = false)
    private Map<String, String> attributes;
}
