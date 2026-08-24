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

import java.math.BigDecimal;

/**
 * One purchased line within an {@link Order}, snapshotted at checkout time (US-2.6) — unlike a
 * cart line, which always resolves price/availability live against the current catalog.
 *
 * <p>{@link #productVariantId} is a plain column, deliberately <b>not</b> a {@code @ManyToOne} FK
 * onto {@link ProductVariant} — {@code ProductServiceImpl.removeVariant} can hard-delete a variant
 * outright, and an already-placed order must remain valid/displayable regardless. {@link #sku}/
 * {@link #productName}/{@link #unitPrice} are copied from the variant/product at the moment of
 * purchase for the same reason: this row must keep telling the truth about what the shopper bought
 * and paid, even if the catalog changes or the variant disappears afterward. {@code lineTotal}
 * ({@link #unitPrice} × {@link #quantity}) is deliberately not a stored column — it's derived at
 * read time by {@code CheckoutMapper}, the same way {@code CartMapper} already derives a cart
 * line's total, rather than persisting a value that could only ever drift from its own inputs.
 */
@Entity
@Table(name = "ORDER_LINE", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "ORDER_LINE_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"order"})
@ToString(exclude = {"order"})
public class OrderLine extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private Order order;

    @Column(name = "PRODUCT_VARIANT_ID", nullable = false)
    private Integer productVariantId;

    @Column(name = "SKU", length = 64, nullable = false)
    private String sku;

    @Column(name = "PRODUCT_NAME", length = 150, nullable = false)
    private String productName;

    @Column(name = "UNIT_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;
}
