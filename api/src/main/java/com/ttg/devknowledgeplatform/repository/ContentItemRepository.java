package com.ttg.devknowledgeplatform.repository;

import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContentItemRepository extends JpaRepository<ContentItem, Integer> {

    Optional<ContentItem> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);
}