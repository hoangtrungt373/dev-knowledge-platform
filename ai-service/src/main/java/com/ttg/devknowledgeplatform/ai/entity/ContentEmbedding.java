package com.ttg.devknowledgeplatform.ai.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "CONTENT_EMBEDDING", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_CONTENT_EMBEDDING_CHUNK",
                columnNames = {"CONTENT_ITEM_ID", "CHUNK_INDEX", "MODEL_NAME"})
})
@AttributeOverride(name = "id", column = @Column(name = "CONTENT_EMBEDDING_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"contentItem"})
@ToString(exclude = {"contentItem", "embedding"})
public class ContentEmbedding extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONTENT_ITEM_ID", nullable = false)
    private ContentItem contentItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE_TYPE", length = 50, nullable = false)
    private ContentType sourceType;

    @Column(name = "CHUNK_INDEX", nullable = false)
    private Short chunkIndex;

    @Column(name = "CHUNK_TEXT", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "EMBEDDING", nullable = false, columnDefinition = "VECTOR")
    private float[] embedding;

    @Column(name = "MODEL_NAME", length = 100, nullable = false)
    private String modelName;

    @Column(name = "DIMENSIONS", nullable = false)
    private Short dimensions;

    @Column(name = "TOKEN_COUNT")
    private Short tokenCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "METADATA", columnDefinition = "JSONB")
    private Map<String, Object> metadata;
}
