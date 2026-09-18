package com.ttg.devknowledgeplatform.devpractice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

/**
 * Manages the problem catalog's lifecycle. Returns entities (never this module's own
 * {@code dto/} classes) — {@code api}'s {@code ProblemMapper} does the entity-to-response
 * mapping, matching {@code content-service}'s {@code ArticleService} convention.
 */
public interface ProblemService {

    Problem create(ProblemCommands.Create command, String authorUuid);

    Problem update(Integer id, ProblemCommands.Update command);

    void delete(Integer id);

    Problem getById(Integer id);

    /**
     * Returns a problem by its slug, but only if it is currently {@link ContentStatus#PUBLISHED}
     * — a draft or archived problem is treated as not found, so a public caller can never
     * confirm the existence of unpublished content by guessing its slug.
     */
    Problem getPublishedBySlug(String slug);

    Page<Problem> list(Pageable pageable, Difficulty difficulty, ContentStatus status, String q);
}
