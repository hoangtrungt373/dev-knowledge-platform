package com.ttg.devknowledgeplatform.devpractice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlan;

/** Study plans — sections and their problem entries are loaded lazily (batched) from the plan. */
@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Integer> {

    boolean existsBySlug(String slug);

    Optional<StudyPlan> findBySlug(String slug);

    /** Every plan in overview order: by position, then name for ties. Plans are few, so unpaged. */
    List<StudyPlan> findAllByOrderByPositionAscNameAsc();
}
