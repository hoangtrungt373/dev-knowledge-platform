package com.ttg.devknowledgeplatform.content.repository;

import com.ttg.devknowledgeplatform.content.entity.ContentItemTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentItemTagRepository extends JpaRepository<ContentItemTag, Integer> {

    boolean existsByTagId(Integer tagId);
}
