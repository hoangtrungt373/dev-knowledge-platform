package com.ttg.devknowledgeplatform.devpractice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;

/** A problem's editorial approaches; their linked runs load lazily (batched). */
@Repository
public interface ProblemSolutionRepository extends JpaRepository<ProblemSolution, Integer> {

    /** A problem's approaches in display order (position, then creation order for ties). */
    List<ProblemSolution> findByProblem_IdOrderByPositionAscIdAsc(Integer problemId);

    /** How many approaches a problem has — a new one is appended after them. */
    int countByProblem_Id(Integer problemId);
}
