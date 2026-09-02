package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import org.hibernate.annotations.BatchSize;

/**
 * The many-to-many join row between {@link ProductCategory} and {@link ProductAttribute} —
 * declares that products in this category are expected to carry this attribute (e.g. "Clothes"
 * assigns both "size" and "color"), optionally {@link #required}. An explicit entity, not a bare
 * {@code @ManyToMany}/{@code @JoinTable}, mirroring {@link ProductTagAssignment}'s own reasoning
 * (audit columns on the join row itself, which a plain join table can't provide).
 *
 * <p>Lifecycle is owned by {@code ProductCategory.categoryAttributes} (cascade {@code ALL},
 * {@code orphanRemoval = true}) — never saved/deleted directly through this entity's own
 * repository outside of that collection, mirroring {@code ProductTagAssignment}'s own ownership by
 * {@code Product.productTagAssignments}.
 *
 * <p>{@code ProductServiceImpl}'s own category-schema enforcement reads this row's
 * {@link #required} flag and its {@link #getAttribute()}'s {@link ProductAttribute#getName()} to
 * validate a product's variants against the product's category — see that class's
 * {@code validateAttributesAgainstCategory}. A category with zero assignments is unconstrained
 * (today's free-form behavior, unchanged); enforcement only turns on once at least one is added.
 */
@Entity
@Table(
        name = "PRODUCT_CATEGORY_ATTRIBUTE",
        schema = "ecommerce",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_PRODUCT_CATEGORY_ATTRIBUTE_PAIR",
                columnNames = {"PRODUCT_CATEGORY_ID", "PRODUCT_ATTRIBUTE_ID"})
)
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_CATEGORY_ATTRIBUTE_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"category", "attribute"})
public class ProductCategoryAttribute extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_CATEGORY_ID", nullable = false)
    private ProductCategory category;

    @BatchSize(size = 32)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ATTRIBUTE_ID", nullable = false)
    private ProductAttribute attribute;

    /** Whether every variant in this category must supply this attribute — enforced at the
     * service layer (not a DB constraint; {@code ProductVariant.attributes} stays a free-form
     * JSONB map regardless — see that field's own Javadoc). */
    @Column(name = "REQUIRED", nullable = false)
    private boolean required;

    /** This assignment's position in the list the admin submitted for this category — not
     * independently editable; see {@code ProductCategoryServiceImpl}. */
    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;
}
