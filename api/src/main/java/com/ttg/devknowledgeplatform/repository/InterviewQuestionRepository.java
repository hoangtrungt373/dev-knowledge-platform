package com.ttg.devknowledgeplatform.repository;

import com.ttg.devknowledgeplatform.common.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface InterviewQuestionRepository
        extends JpaRepository<InterviewQuestion, Integer>, JpaSpecificationExecutor<InterviewQuestion> {
}