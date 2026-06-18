package com.ttg.devknowledgeplatform.ai.repository;

import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContentEmbeddingRepository extends JpaRepository<ContentEmbedding, Integer> {

    List<ContentEmbedding> findByContentItem_Id(Integer contentItemId);

    boolean existsByContentItem_IdAndModelName(Integer contentItemId, String modelName);

    @Modifying
    @Query("DELETE FROM ContentEmbedding ce WHERE ce.contentItem.id = :contentItemId AND ce.modelName = :modelName")
    void deleteByContentItem_IdAndModelName(@Param("contentItemId") Integer contentItemId,
                                            @Param("modelName") String modelName);

    @Modifying
    @Query("DELETE FROM ContentEmbedding ce WHERE ce.contentItem.id = :contentItemId")
    void deleteByContentItem_Id(@Param("contentItemId") Integer contentItemId);
}
