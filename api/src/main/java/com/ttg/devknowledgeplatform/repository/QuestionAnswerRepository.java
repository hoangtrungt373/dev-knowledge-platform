package com.ttg.devknowledgeplatform.repository;

import com.ttg.devknowledgeplatform.common.entity.QuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionAnswerRepository
        extends JpaRepository<QuestionAnswer, Integer>, JpaSpecificationExecutor<QuestionAnswer> {

    Optional<QuestionAnswer> findByContentItem_Slug(String slug);

    Optional<QuestionAnswer> findByContentItem_Id(Integer contentItemId);
}
