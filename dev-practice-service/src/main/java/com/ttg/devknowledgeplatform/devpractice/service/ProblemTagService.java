package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;

/** Manages the problem-tag catalog (Array, Math, Stack, ...). */
public interface ProblemTagService {

    /**
     * @param name the tag's display name (trimmed; must be unique, case-insensitively)
     * @return the created tag, with a slug generated from the name
     */
    ProblemTag create(String name);

    /** Renames a tag (regenerating its slug); a rename is visible on every problem using it. */
    ProblemTag update(Integer id, String name);

    /** Deletes a tag — refused with {@code PROBLEM_TAG_IN_USE} while any problem still uses it. */
    void delete(Integer id);

    /** @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException {@code PROBLEM_TAG_NOT_FOUND} */
    ProblemTag getById(Integer id);

    /** Paginated, optionally filtered by a case-insensitive name substring. */
    Page<ProblemTag> list(Pageable pageable, String q);

    /** Every tag, sorted by name — for pickers and the public filter, where the catalog is small. */
    List<ProblemTag> listAll();
}
