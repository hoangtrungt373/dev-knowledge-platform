package com.ttg.devknowledgeplatform.devpractice.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;

@Repository
public interface ProblemTagRepository extends JpaRepository<ProblemTag, Integer> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    /** Resolves a seed file's tag names ({@code service.seed.ProblemSeeder}). */
    Optional<ProblemTag> findByNameIgnoreCase(String name);

    /** The admin list's title search. */
    Page<ProblemTag> findByNameContainingIgnoreCase(String q, Pageable pageable);
}
