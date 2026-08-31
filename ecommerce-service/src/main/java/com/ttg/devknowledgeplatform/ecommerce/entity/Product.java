package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
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
 *
 * <p>{@link #productTagAssignments}, by contrast, *is* cascade-owned here (see the field's own
 * Javadoc) — mirrors {@code content-service}'s {@code ContentItem.contentItemTags} ownership of
 * its own many-to-many join rows.
 */
@Entity
@Table(name = "PRODUCT", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "PRODUCT_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"productCategory", "variants", "images", "productTagAssignments"})
@ToString(exclude = {"productCategory", "variants", "images", "productTagAssignments"})
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

    /**
     * The product's tag assignments — cascade-owned here (unlike {@link #variants}/{@link #images}
     * above): {@code ProductServiceImpl.applyTagIds} clears and rebuilds this collection directly
     * rather than going through {@code ProductTagAssignmentRepository} on its own, the same way
     * {@code content-service}'s {@code ContentItem.contentItemTags} is managed.
     */
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductTagAssignment> productTagAssignments = new ArrayList<>();
}
