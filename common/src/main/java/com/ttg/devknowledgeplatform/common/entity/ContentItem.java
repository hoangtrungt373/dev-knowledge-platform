package com.ttg.devknowledgeplatform.common.entity;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CONTENT_ITEM", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "CONTENT_ITEM_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"category", "contentItemTags"})
@ToString(exclude = {"category", "contentItemTags"})
public class ContentItem extends AbstractEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 50, nullable = false)
    private ContentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50, nullable = false)
    private ContentStatus status = ContentStatus.DRAFT;

    @Column(name = "TITLE", length = 500, nullable = false)
    private String title;

    @Column(name = "SLUG", length = 500, nullable = false, unique = true)
    private String slug;

    @Column(name = "AUTHOR_ID")
    private Integer authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CATEGORY_ID")
    private Category category;

    @Column(name = "VIEW_COUNT", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "PUBLISHED_AT")
    private Instant publishedAt;

    /**
     * Mean cosine similarity between this document's chunk embeddings and the corpus centroid,
     * computed by {@code IndexingQualityServiceImpl} immediately after ingestion.
     *
     * <p>{@code null} indicates the document has not been assessed yet (pre-existing content
     * or cold-start with no corpus centroid available). A non-null value below
     * {@code app.ai.embedding.indexing-coherence-threshold} signals a low-quality document
     * (corrupted OCR, random HTML, off-domain content). The raw score is stored rather than
     * a boolean flag so that the interpretation threshold can be changed in config without
     * requiring a DB migration or re-assessment.
     */
    @Column(name = "QUALITY_SCORE", precision = 5, scale = 4)
    private Double qualityScore;

    @BatchSize(size = 32)
    @OneToMany(mappedBy = "contentItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ContentItemTag> contentItemTags = new HashSet<>();
}
