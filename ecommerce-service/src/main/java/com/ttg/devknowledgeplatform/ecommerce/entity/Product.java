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
 * A sellable product in the storefront catalog.
 *
 * <p>Always has at least one {@link ProductVariant} — there is no such thing as a variant-less
 * product in this domain (see {@code docs/user-stories/01-catalog-search.md}, US-1.6). Setting
 * {@link #active} to {@code false} soft-deletes the product: it disappears from browse/search
 * (via the {@link ProductSearchView} projection) but existing order line items and reviews still
 * resolve it by id.
 *
 * <p>{@link #variants}/{@link #images} are read-side navigation only, mirroring
 * {@code content-service}'s {@code Category.children} — no cascade. Creating/updating a variant
 * or image still goes through its own repository directly (e.g.
 * {@code ProductVariantRepository.save}), not by mutating these collections and saving the
 * product.
 */
@Entity
@Table(name = "PRODUCT", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"productCategory", "variants", "images"})
@ToString(exclude = {"productCategory", "variants", "images"})
public class Product extends AbstractEntity {

    @Column(name = "NAME", length = 150, nullable = false)
    private String name;

    @Column(name = "DESCRIPTION", columnDefinition = "TEXT")
    private String description;

    @Column(name = "SLUG", length = 150, nullable = false, unique = true)
    private String slug;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_CATEGORY_ID", nullable = false)
    private ProductCategory productCategory;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductImage> images = new ArrayList<>();
}
