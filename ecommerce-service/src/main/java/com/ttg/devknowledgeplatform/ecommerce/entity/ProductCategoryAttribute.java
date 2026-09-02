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
 * <p><strong>Advisory only — not enforced against {@code ProductVariant.attributes}.</strong> The
 * admin GUI reads this row's {@link #required} flag and its {@link #getAttribute()}'s {@link
 * ProductAttribute#getName()} to pre-fill/suggest a variant's attribute rows for products in this
 * category (see {@code gui}'s {@code useCategoryAttributeSuggestions}), but
 * {@code ProductServiceImpl} never validates a variant's actual {@code attributes} map against
 * this schema — an admin may freely key/value anything on a variant regardless of what its
 * category suggests here. This was a deliberate reversal of an earlier "enforced" design (see
 * {@code ProductServiceImpl}'s own class Javadoc and {@code ecommerce-service/CLAUDE.md}'s note on
 * this feature).
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

    /** Whether every variant in this category is *suggested* to supply this attribute — advisory
     * only (see this class's own Javadoc), surfaced by the admin GUI, never enforced against
     * {@code ProductVariant.attributes} (a free-form JSONB map regardless — see that field's own
     * Javadoc). */
    @Column(name = "REQUIRED", nullable = false)
    private boolean required;

    /** This assignment's position in the list the admin submitted for this category — not
     * independently editable; see {@code ProductCategoryServiceImpl}. */
    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;
}
