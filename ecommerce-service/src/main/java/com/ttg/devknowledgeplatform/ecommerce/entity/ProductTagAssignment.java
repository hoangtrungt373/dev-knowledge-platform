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
 * The many-to-many join row between {@link Product} and {@link ProductTag} — an explicit entity,
 * not a bare {@code @ManyToMany}/{@code @JoinTable}, so the assignment itself carries the same
 * audit columns (usrCreation/dteCreation/etc.) every other entity in this reactor does, which a
 * plain join table can't provide. Mirrors {@code content-service}'s {@code ContentItemTag} exactly
 * — same shape, same reasoning, just for products instead of content items.
 *
 * <p>Lifecycle is owned by {@code Product.productTagAssignments} (cascade {@code ALL},
 * {@code orphanRemoval = true}) — never saved/deleted directly through this entity's own
 * repository outside of that collection, mirroring {@code ContentItemTag}'s ownership by
 * {@code ContentItem.contentItemTags}.
 */
@Entity
@Table(
        name = "PRODUCT_TAG_ASSIGNMENT",
        schema = "ecommerce",
        uniqueConstraints = @UniqueConstraint(name = "UK_PRODUCT_TAG_ASSIGNMENT_PAIR", columnNames = {"PRODUCT_ID", "PRODUCT_TAG_ID"})
)
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_TAG_ASSIGNMENT_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"product", "productTag"})
public class ProductTagAssignment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @BatchSize(size = 32)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_TAG_ID", nullable = false)
    private ProductTag productTag;
}
