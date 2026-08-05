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

/**
 * One image in a {@link Product}'s ordered gallery.
 *
 * <p>{@link #storageKey} references the actual file in MinIO via {@code infra}'s
 * {@code StorageService} — this entity only ever stores the object key and its position in the
 * gallery, never file bytes. {@link #sortOrder} is unique per product so gallery order is
 * unambiguous and a reorder is a single update, not a full-list rewrite.
 */
@Entity
@Table(
        name = "PRODUCT_IMAGE",
        schema = "ecommerce",
        uniqueConstraints = @UniqueConstraint(name = "UK_PRODUCT_IMAGE_PRODUCT_SORT_ORDER", columnNames = {"PRODUCT_ID", "SORT_ORDER"})
)
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_IMAGE_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"product"})
@ToString(exclude = {"product"})
public class ProductImage extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Column(name = "STORAGE_KEY", length = 255, nullable = false)
    private String storageKey;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;
}
