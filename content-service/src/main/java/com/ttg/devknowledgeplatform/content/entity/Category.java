package com.ttg.devknowledgeplatform.content.entity;

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

@Entity
@Table(name = "CATEGORY", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "CATEGORY_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"parent", "children"})
@ToString(exclude = {"parent", "children"})
public class Category extends AbstractEntity {

    // Uniqueness is enforced in the DB as a case-insensitive functional index on LOWER(NAME)
    // (matches CategoryServiceImpl's existsByNameIgnoreCase) — not expressible as unique = true
    // here, which would generate a plain case-sensitive constraint instead.
    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @Column(name = "SLUG", length = 100, nullable = false, unique = true)
    private String slug;

    // Null for every user/admin-created row; set only by CategorySeeder, purely to detect
    // "already seeded" across re-runs without depending on NAME/SLUG staying unchanged.
    @Column(name = "SEED_ID", length = 100)
    private String seedId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID")
    private Category parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<Category> children = new ArrayList<>();
}
