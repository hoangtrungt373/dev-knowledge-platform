package com.ttg.devknowledgeplatform.ai.repository;

import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContentEmbeddingRepository extends JpaRepository<ContentEmbedding, Integer> {

    List<ContentEmbedding> findByContentItem_Id(Integer contentItemId);

    /**
     * Returns the IDs of the top-K most similar embeddings using pgvector cosine distance.
     * Embedding must be formatted as a pgvector string: [x,y,z,...].
     */
    @Query(value = """
            SELECT content_embedding_id
            FROM product.content_embedding
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Integer> findTopSimilarIds(@Param("embedding") String embedding, @Param("limit") int limit);

    /** Loads embeddings with their content item eagerly to avoid lazy-init issues. */
    @Query("SELECT ce FROM ContentEmbedding ce JOIN FETCH ce.contentItem WHERE ce.id IN :ids")
    List<ContentEmbedding> findAllByIdWithContentItem(@Param("ids") List<Integer> ids);

    boolean existsByContentItem_IdAndModelName(Integer contentItemId, String modelName);

    @Modifying
    @Query("DELETE FROM ContentEmbedding ce WHERE ce.contentItem.id = :contentItemId AND ce.modelName = :modelName")
    void deleteByContentItem_IdAndModelName(@Param("contentItemId") Integer contentItemId,
                                            @Param("modelName") String modelName);

    @Modifying
    @Query("DELETE FROM ContentEmbedding ce WHERE ce.contentItem.id = :contentItemId")
    void deleteByContentItem_Id(@Param("contentItemId") Integer contentItemId);

    /**
     * Computes the average embedding vector for all chunks of the given source type.
     *
     * <p>Uses the pgvector {@code avg()} aggregate. The result is cast to text using
     * pgvector notation {@code [f1,f2,...,f1536]}, ready to be stored directly in
     * {@code SysParam.value}. Returns {@code null} when no rows match (e.g. that
     * content type has not been indexed yet).
     *
     * @param sourceType the {@code SOURCE_TYPE} column value
     *                   (e.g. {@code "ARTICLE"}, {@code "INTERVIEW_QUESTION"}, {@code "BLOG_POST"})
     * @return pgvector text representation of the centroid, or {@code null}
     */
    @Query(value = """
            SELECT avg(embedding)::text
            FROM product.content_embedding
            WHERE source_type = :sourceType
            """, nativeQuery = true)
    String computeCentroidBySourceType(@Param("sourceType") String sourceType);

    /**
     * Computes the average embedding vector across all content embeddings regardless of source type.
     *
     * <p>Produces the global corpus centroid ({@code CENTROID_ALL}) used during anomaly
     * detection for unfiltered queries. Returns {@code null} if the table is empty.
     *
     * @return pgvector text representation of the global centroid, or {@code null}
     */
    @Query(value = """
            SELECT avg(embedding)::text
            FROM product.content_embedding
            """, nativeQuery = true)
    String computeGlobalCentroid();
}
