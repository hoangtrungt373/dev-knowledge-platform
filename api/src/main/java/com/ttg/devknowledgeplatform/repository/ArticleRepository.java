package com.ttg.devknowledgeplatform.repository;

import com.ttg.devknowledgeplatform.common.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Integer>, JpaSpecificationExecutor<Article> {

    Optional<Article> findByContentItem_Slug(String slug);

    Optional<Article> findByContentItem_Id(Integer contentItemId);

    boolean existsByContentItemId(Integer contentItemId);
}
