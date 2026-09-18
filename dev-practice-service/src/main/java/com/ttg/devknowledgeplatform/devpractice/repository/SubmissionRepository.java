package com.ttg.devknowledgeplatform.devpractice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.devpractice.entity.Submission;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Integer> {

    Page<Submission> findByUserUuid(String userUuid, Pageable pageable);

    Page<Submission> findByUserUuidAndProblem_Id(String userUuid, Integer problemId, Pageable pageable);
}
