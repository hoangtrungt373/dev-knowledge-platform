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
}
