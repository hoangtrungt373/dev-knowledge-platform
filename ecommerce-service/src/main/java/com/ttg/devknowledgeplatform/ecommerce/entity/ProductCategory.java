package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A flat product category for browsing/filtering the storefront catalog.
 *
 * <p>Deliberately named {@code ProductCategory} (table {@code PRODUCT_CATEGORY}), not
 * {@code Category}, to avoid colliding with {@code content-service}'s {@code Category} entity
 * (table {@code CATEGORY}) — both live in the shared {@code product} schema, and the two concepts
 * (product taxonomy vs. knowledge-base taxonomy) are unrelated. Flat by design: no parent/child
 * hierarchy, unlike {@code content-service}'s {@code Category}.
 */
@Entity
@Table(name = "PRODUCT_CATEGORY", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_CATEGORY_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProductCategory extends AbstractEntity {

    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @Column(name = "SLUG", length = 100, nullable = false, unique = true)
    private String slug;
}
