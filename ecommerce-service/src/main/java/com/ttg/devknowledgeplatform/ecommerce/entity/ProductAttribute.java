package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * A reusable attribute concept shared across categories — e.g. "color" — with a controlled
 * vocabulary of {@link ProductAttributeValue}s ("Red"/"Blue"/"Black"), assigned to whichever
 * {@link ProductCategory} rows need it via {@link ProductCategoryAttribute} (many-to-many, with
 * a per-category {@code required} flag and ordering). This is the "Option B" global attribute
 * registry — chosen over a simpler "each category types its own attribute names inline"
 * alternative specifically so a concept like "Color" has exactly one spelling and one shared value
 * list everywhere it's assigned, rather than being independently (and inconsistently) typed per
 * category.
 *
 * <p>{@link #name} is meant to be typed exactly as it should appear as a key in {@link
 * ProductVariant#getAttributes()} (e.g. {@code "size"}, {@code "color"}, {@code "model"}) —
 * matching against a variant's attribute map is a literal, case-sensitive string comparison, done
 * in {@code ProductServiceImpl}'s own category-schema enforcement (see that class's
 * {@code validateAttributesAgainstCategory}), not a lookup by id — a variant's {@code attributes}
 * map has no room to reference this entity's primary key.
 */
@Entity
@Table(name = "PRODUCT_ATTRIBUTE", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_ATTRIBUTE_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"values", "categoryAssignments"})
@ToString(exclude = {"values", "categoryAssignments"})
public class ProductAttribute extends AbstractEntity {

    // Uniqueness is enforced in the DB as a case-insensitive functional index on LOWER(NAME)
    // (matches ProductAttributeServiceImpl's existsByNameIgnoreCase) — mirrors ProductTag's own
    // name-uniqueness treatment exactly; not expressible as unique = true here, which would
    // generate a plain case-sensitive constraint instead.
    @Column(name = "NAME", length = 50, nullable = false)
    private String name;

    /** Cascade-owned here — an attribute's values have no lifecycle independent of the attribute
     * itself (unlike e.g. {@code Product.variants}, which has its own repository and no cascade).
     * Ordered by {@link ProductAttributeValue#getDisplayOrder()}, which mirrors each value's
     * position in the list the admin submitted (see {@code ProductAttributeServiceImpl}) rather
     * than a caller-supplied number of its own. */
    @OneToMany(mappedBy = "attribute", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductAttributeValue> values = new ArrayList<>();

    // Navigation-only — lifecycle is owned by ProductCategory.categoryAttributes (cascade ALL,
    // orphanRemoval), mirroring ProductTag.productTagAssignments' own read-only-navigation shape
    // (lifecycle owned by the *other* side of the many-to-many, Product.productTagAssignments).
    @OneToMany(mappedBy = "attribute", fetch = FetchType.LAZY)
    private List<ProductCategoryAttribute> categoryAssignments = new ArrayList<>();
}
