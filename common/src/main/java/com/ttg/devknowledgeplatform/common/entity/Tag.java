package com.ttg.devknowledgeplatform.common.entity;

import java.util.HashSet;
import java.util.Set;

import com.ttg.devknowledgeplatform.common.enums.TagStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "TAG", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "TAG_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "contentItemTags")
public class Tag extends AbstractEntity {

    @NotNull
    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @NotNull
    @Column(name = "SLUG", length = 100, nullable = false)
    private String slug;

    // Null for every user/admin-created row; set only by TagSeeder, purely to detect "already
    // seeded" across re-runs without depending on NAME/SLUG staying unchanged.
    @Column(name = "SEED_ID", length = 100)
    private String seedId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 10, nullable = false)
    private TagStatus status = TagStatus.ACTIVE;

    // Navigation-only — lifecycle is owned by ContentItem.contentItemTags
    @OneToMany(mappedBy = "tag", fetch = FetchType.LAZY)
    private Set<ContentItemTag> contentItemTags = new HashSet<>();
}
