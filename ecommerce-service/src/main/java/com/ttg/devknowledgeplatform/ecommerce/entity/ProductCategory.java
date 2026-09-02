package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A product category for browsing/filtering the storefront catalog, supporting an optional
 * parent/child hierarchy (e.g. "Furniture" -&gt; "Outdoor furniture").
 *
 * <p>Deliberately named {@code ProductCategory} (table {@code PRODUCT_CATEGORY}), not
 * {@code Category}, to avoid colliding with {@code content-service}'s {@code Category} entity
 * (table {@code CATEGORY}) — the naming choice predates this module's extraction into its own
 * database ({@code ecommerce} schema, separate from the monolith's shared {@code product} schema
 * {@code content-service} still uses), and is kept regardless, since the two concepts (product
 * taxonomy vs. knowledge-base taxonomy) are unrelated either way. The hierarchy shape itself
 * (self-referential {@code parent}/{@code children}, adjacency-list style) mirrors
 * {@code content-service}'s own {@code Category} exactly — same {@code parent}/{@code children}
 * exclusion from {@code equals}/{@code hashCode}/{@code toString} to avoid lazy-init/recursion
 * issues, same {@code ProductCategoryServiceImpl.validateParentAssignment}-style cycle guard (see
 * that class).
 *
 * <p>{@link #categoryAttributes} is the "Option B" global-attribute-registry follow-up — a
 * category's own schema of expected {@link ProductAttribute}s (e.g. "Clothes" assigns "size" and
 * "color"), cascade-owned here (unlike {@link #children} above, which is read-side navigation
 * only) exactly the way {@code Product.productTagAssignments} owns its own many-to-many join rows.
 */
@Entity
@Table(name = "PRODUCT_CATEGORY", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_CATEGORY_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"parent", "children", "categoryAttributes"})
@ToString(exclude = {"parent", "children", "categoryAttributes"})
public class ProductCategory extends AbstractEntity {

    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @Column(name = "SLUG", length = 100, nullable = false, unique = true)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_CATEGORY_ID")
    private ProductCategory parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<ProductCategory> children = new ArrayList<>();

    /**
     * This category's own attribute schema — cascade-owned here (unlike {@link #children}, which
     * is read-side navigation only): {@code ProductCategoryServiceImpl.applyCategoryAttributes}
     * clears and rebuilds this collection directly, the same way {@code Product
     * .applyTagIds} manages {@code Product.productTagAssignments}. Ordered by
     * {@link ProductCategoryAttribute#getDisplayOrder()}, which mirrors each assignment's position
     * in the list the admin submitted, not a caller-supplied number of its own.
     */
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductCategoryAttribute> categoryAttributes = new ArrayList<>();
}
