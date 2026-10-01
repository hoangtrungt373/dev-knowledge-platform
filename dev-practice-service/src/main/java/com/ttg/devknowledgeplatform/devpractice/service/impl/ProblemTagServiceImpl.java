package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagAssignmentRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemTagService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Problem-tag catalog CRUD — mirrors {@code ecommerce-service}'s {@code ProductTagServiceImpl}:
 * case-insensitively unique names, slugs regenerated on rename, delete refused while in use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProblemTagServiceImpl implements ProblemTagService {

    private final ProblemTagRepository problemTagRepository;
    private final ProblemTagAssignmentRepository problemTagAssignmentRepository;
    private final SlugService slugService;

    @Override
    public ProblemTag create(String name) {
        String normalized = name.trim();
        // (Object) casts: a lone String argument would bind to Validator's (..., String message)
        // overload and replace the template instead of filling {0} — see this module's CLAUDE.md.
        Validator.isFalse(problemTagRepository.existsByNameIgnoreCase(normalized),
                DevPracticeErrorCode.PROBLEM_TAG_NAME_CONFLICT, (Object) normalized);
        String slug = slugService.generateUniqueSlug(
                normalized, problemTagRepository::existsBySlug, DevPracticeErrorCode.PROBLEM_TAG_SLUG_CONFLICT);

        ProblemTag saved = problemTagRepository.save(ProblemTag.builder().name(normalized).slug(slug).build());
        log.info("Created problem tag id={} slug={}", saved.getId(), slug);
        return saved;
    }

    @Override
    public ProblemTag update(Integer id, String name) {
        ProblemTag tag = findById(id);
        String normalized = name.trim();

        if (!tag.getName().equals(normalized)) {
            Validator.isFalse(problemTagRepository.existsByNameIgnoreCaseAndIdNot(normalized, id),
                    DevPracticeErrorCode.PROBLEM_TAG_NAME_CONFLICT, (Object) normalized);
            // A pure case change ("array" -> "Array") keeps the slug, which is lower-case anyway.
            if (!tag.getName().equalsIgnoreCase(normalized)) {
                tag.setSlug(slugService.generateUniqueSlug(
                        normalized, problemTagRepository::existsBySlugAndIdNot, id,
                        DevPracticeErrorCode.PROBLEM_TAG_SLUG_CONFLICT));
            }
            tag.setName(normalized);
        }

        ProblemTag updated = problemTagRepository.save(tag);
        log.info("Updated problem tag id={}", id);
        return updated;
    }

    @Override
    public void delete(Integer id) {
        ProblemTag tag = findById(id);
        long usage = problemTagAssignmentRepository.countByProblemTag_Id(id);
        Validator.isTrue(usage == 0, DevPracticeErrorCode.PROBLEM_TAG_IN_USE, usage);
        problemTagRepository.delete(tag);
        log.info("Deleted problem tag id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public ProblemTag getById(Integer id) {
        return findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemTag> list(Pageable pageable, String q) {
        return q == null || q.isBlank()
                ? problemTagRepository.findAll(pageable)
                : problemTagRepository.findByNameContainingIgnoreCase(q.trim(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemTag> listAll() {
        return problemTagRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    private ProblemTag findById(Integer id) {
        return Validator.notFound(problemTagRepository.findById(id), DevPracticeErrorCode.PROBLEM_TAG_NOT_FOUND, id);
    }
}
