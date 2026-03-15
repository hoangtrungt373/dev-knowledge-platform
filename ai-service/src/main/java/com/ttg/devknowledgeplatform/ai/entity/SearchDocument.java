package com.ttg.devknowledgeplatform.ai.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "SEARCH_DOCUMENT")
@AttributeOverride(name = "id", column = @Column(name = "SEARCH_DOCUMENT_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"contentItem"})
@ToString(exclude = {"contentItem"})
public class SearchDocument extends AbstractEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONTENT_ITEM_ID", nullable = false, unique = true)
    private ContentItem contentItem;

    @Column(name = "TITLE", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "BODY_PLAIN", nullable = false, columnDefinition = "TEXT")
    private String bodyPlain;

    /**
     * Managed via native SQL — populated by to_tsvector() on insert/update.
     * Not directly writable from JPA; use native queries to maintain this column.
     */
    @Column(name = "SEARCH_VECTOR", nullable = false, columnDefinition = "TSVECTOR", insertable = false, updatable = false)
    private String searchVector;
}
