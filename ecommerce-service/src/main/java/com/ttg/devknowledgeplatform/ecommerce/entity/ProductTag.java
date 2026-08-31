package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * A flat, free-form label a product can be tagged with — a product may have any number of tags,
 * and a tag may be attached to any number of products (many-to-many, via {@link ProductTagAssignment}).
 *
 * <p>Deliberately just {@code name}/{@code slug} — no {@code status}/lifecycle field, unlike
 * {@code content-service}'s own {@code Tag} (which has {@code TagStatus}). Same "keep it simple
 * until a concrete need shows up" call this module already made for {@code ProductCategory} before
 * it gained hierarchy support; add a status here the same way, if one is ever actually needed.
 */
@Entity
@Table(name = "PRODUCT_TAG", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_TAG_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "productTagAssignments")
@ToString(exclude = "productTagAssignments")
public class ProductTag extends AbstractEntity {

    // Uniqueness is enforced in the DB as a case-insensitive functional index on LOWER(NAME)
    // (matches ProductTagServiceImpl's existsByNameIgnoreCase) — not expressible as unique = true
    // here, which would generate a plain case-sensitive constraint instead.
    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @Column(name = "SLUG", length = 100, nullable = false, unique = true)
    private String slug;

    // Navigation-only — lifecycle is owned by Product.productTagAssignments (cascade ALL,
    // orphanRemoval) the same way ContentItem.contentItemTags owns its own side.
    @OneToMany(mappedBy = "productTag", fetch = FetchType.LAZY)
    private List<ProductTagAssignment> productTagAssignments = new ArrayList<>();
}
