package com.ttg.devknowledgeplatform.devpractice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTagAssignment;

/**
 * Read-only outside {@code Problem.tagAssignments}' own cascade — the only write path. Backs
 * {@code ProblemTagServiceImpl#delete}'s in-use guard.
 */
@Repository
public interface ProblemTagAssignmentRepository extends JpaRepository<ProblemTagAssignment, Integer> {

    long countByProblemTag_Id(Integer problemTagId);
}
