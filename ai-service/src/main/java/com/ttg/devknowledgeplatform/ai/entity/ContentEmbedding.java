package com.ttg.devknowledgeplatform.ai.entity;

import com.ttg.devknowledgeplatform.ai.converter.FloatArrayToVectorConverter;
import com.ttg.devknowledgeplatform.ai.converter.PgVectorJdbcType;
import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import com.ttg.devknowledgeplatform.content.enums.ContentType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "CONTENT_EMBEDDING"
)
@AttributeOverride(name = "id", column = @Column(name = "CONTENT_EMBEDDING_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"contentItem"})
@ToString(exclude = {"contentItem", "embedding"})
public class ContentEmbedding extends AbstractEntity {

    /** Dimension produced by OpenAI text-embedding-3-small. Change here and in the migration if switching models. */
    public static final int EMBEDDING_DIMENSIONS = 1536;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONTENT_ITEM_ID", nullable = false)
    private ContentItem contentItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE_TYPE", length = 50, nullable = false)
    private ContentType sourceType;

    @Column(name = "CHUNK_INDEX", nullable = false)
    private Integer chunkIndex;

    @Column(name = "CHUNK_TEXT", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    // PgVectorJdbcType binds the converted String via setObject(index, value, Types.OTHER)
    // instead of setString(...) — required for pgvector: a plain varchar-typed bind parameter
    // doesn't implicitly cast to `vector` the way a string literal written directly in SQL text
    // does. @JdbcTypeCode(SqlTypes.OTHER) alone does NOT work here — see PgVectorJdbcType's
    // javadoc for why a custom JdbcType is needed instead of that annotation.
    @Convert(converter = FloatArrayToVectorConverter.class)
    @org.hibernate.annotations.JdbcType(PgVectorJdbcType.class)
    @Column(name = "EMBEDDING", nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "MODEL_NAME", length = 100, nullable = false)
    private String modelName;

    @Column(name = "DIMENSIONS", nullable = false)
    private Integer dimensions;

    @Column(name = "TOKEN_COUNT")
    private Integer tokenCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "METADATA", columnDefinition = "JSONB")
    private ContentEmbeddingMetadata metadata;
}
