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
import org.hibernate.annotations.BatchSize;

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
 *
 * <p><b>Bug fix: all three collections gained {@code @BatchSize(size = 20)}.</b> {@code
 * mapper.ProductMapper#toResponse} maps all three unconditionally — including on the paginated
 * admin list ({@code service.impl.ProductServiceImpl#list}), where a 20-row page used to trigger
 * up to 60 extra lazy-load {@code SELECT}s (one per collection per row), silently — {@code
 * spring.jpa.open-in-view} means nothing ever errors, it just gets slower as the catalog grows.
 * {@code @BatchSize} doesn't change *what* gets fetched (still lazy, still one query per distinct
 * collection the first time something in the page actually reads it) — it changes *how many rows
 * that query covers*: Hibernate batches every not-yet-initialized collection of the same type
 * still pending in the current persistence context into one {@code WHERE product_id IN (...)}
 * query instead of one query per product. A 20-row page now costs at most 3 extra queries total
 * (one per collection type) instead of up to 60, regardless of how many rows are on the page — and
 * every other lazy-load site for these same collections benefits identically, not just this one
 * list endpoint, since the annotation lives on the mapping itself rather than one query hint.
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
    @BatchSize(size = 20)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    private List<ProductImage> images = new ArrayList<>();

    /**
     * The product's tag assignments — cascade-owned here (unlike {@link #variants}/{@link #images}
     * above): {@code ProductServiceImpl.applyTagIds} clears and rebuilds this collection directly
     * rather than going through {@code ProductTagAssignmentRepository} on its own, the same way
     * {@code content-service}'s {@code ContentItem.contentItemTags} is managed.
     */
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<ProductTagAssignment> productTagAssignments = new ArrayList<>();
}
