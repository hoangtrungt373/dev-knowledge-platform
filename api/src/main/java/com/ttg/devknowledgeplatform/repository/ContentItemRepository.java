package com.ttg.devknowledgeplatform.repository;

import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContentItemRepository extends JpaRepository<ContentItem, Integer> {

    Optional<ContentItem> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);

    boolean existsByCategoryId(Integer categoryId);

    @Modifying
    @Query("UPDATE ContentItem c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    void incrementViewCount(@Param("id") Integer id);
}