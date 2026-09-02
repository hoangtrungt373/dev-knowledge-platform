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

/**
 * One allowed value in a {@link ProductAttribute}'s controlled vocabulary — e.g. {@code "Red"} for
 * the "Color" attribute. Lifecycle is owned by {@code ProductAttribute.values} (cascade
 * {@code ALL}, {@code orphanRemoval = true}) — never saved/deleted directly through this entity's
 * own repository outside of that collection.
 */
@Entity
@Table(name = "PRODUCT_ATTRIBUTE_VALUE", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_ATTRIBUTE_VALUE_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "attribute")
@ToString(exclude = "attribute")
public class ProductAttributeValue extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ATTRIBUTE_ID", nullable = false)
    private ProductAttribute attribute;

    @Column(name = "VALUE", length = 50, nullable = false)
    private String value;

    /** This value's position in the list the admin submitted for the owning attribute — not
     * independently editable; see {@code ProductAttributeServiceImpl}. */
    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;
}
