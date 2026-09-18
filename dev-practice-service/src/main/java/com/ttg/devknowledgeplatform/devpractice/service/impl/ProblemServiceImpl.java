package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.spec.ProblemSpecification;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProblemServiceImpl implements ProblemService {

    private final ProblemRepository problemRepository;
    private final SlugService slugService;

    @Override
    public Problem create(ProblemCommands.Create command, String authorUuid) {
        String slug = slugService.generateUniqueSlug(
                command.title(), problemRepository::existsBySlug, DevPracticeErrorCode.PROBLEM_SLUG_CONFLICT);
        ContentStatus status = command.status() != null ? command.status() : ContentStatus.DRAFT;

        Problem problem = Problem.builder()
                .title(command.title())
                .slug(slug)
                .description(command.description())
                .difficulty(command.difficulty())
                .status(status)
                .authorUuid(authorUuid)
                .publishedAt(ContentStatus.PUBLISHED.equals(status) ? Instant.now() : null)
                .build();
        replaceTestCases(problem, command.testCases());

        Problem saved = problemRepository.save(problem);
        log.info("User {} created problem {} slug={}", authorUuid, saved.getId(), slug);
        return saved;
    }

    @Override
    public Problem update(Integer id, ProblemCommands.Update command) {
        Problem problem = findById(id);

        if (!problem.getTitle().equals(command.title())) {
            problem.setSlug(slugService.generateUniqueSlug(
                    command.title(), problemRepository::existsBySlugAndIdNot, id, DevPracticeErrorCode.PROBLEM_SLUG_CONFLICT));
        }
        problem.setTitle(command.title());
        problem.setDescription(command.description());
        problem.setDifficulty(command.difficulty());

        ContentStatus prevStatus = problem.getStatus();
        ContentStatus newStatus = command.status() != null ? command.status() : prevStatus;
        problem.setStatus(newStatus);
        if (ContentStatus.PUBLISHED.equals(newStatus) && !ContentStatus.PUBLISHED.equals(prevStatus)
                && problem.getPublishedAt() == null) {
            problem.setPublishedAt(Instant.now());
        }

        replaceTestCases(problem, command.testCases());

        Problem updated = problemRepository.save(problem);
        log.info("Updated problem {}", id);
        return updated;
    }

    @Override
    public void delete(Integer id) {
        Problem problem = findById(id);
        problemRepository.delete(problem);
        log.info("Deleted problem {}", id);
    }

    @Override
    public Problem getById(Integer id) {
        return findById(id);
    }

    @Override
    public Problem getPublishedBySlug(String slug) {
        Optional<Problem> problem = problemRepository.findBySlug(slug)
                .filter(p -> ContentStatus.PUBLISHED.equals(p.getStatus()));
        return Validator.notFound(problem, DevPracticeErrorCode.PROBLEM_NOT_FOUND, slug);
    }

    @Override
    public Page<Problem> list(Pageable pageable, Difficulty difficulty, ContentStatus status, String q) {
        return problemRepository.findAll(ProblemSpecification.withFilters(difficulty, status, q), pageable);
    }

    private Problem findById(Integer id) {
        return Validator.notFound(problemRepository.findById(id), DevPracticeErrorCode.PROBLEM_NOT_FOUND, id);
    }

    /** Replace-all: clears the current test-case list (orphanRemoval deletes the old rows on save) and rebuilds it. */
    private void replaceTestCases(Problem problem, List<ProblemCommands.TestCaseInput> inputs) {
        problem.getTestCases().clear();
        for (ProblemCommands.TestCaseInput input : inputs) {
            TestCase testCase = TestCase.builder()
                    .problem(problem)
                    .input(input.input())
                    .expectedOutput(input.expectedOutput())
                    .sample(input.sample())
                    .build();
            problem.getTestCases().add(testCase);
        }
    }
}
