package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
 */
@Entity
@Table(name = "PRODUCT_CATEGORY", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_CATEGORY_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"parent", "children"})
@ToString(exclude = {"parent", "children"})
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
}
