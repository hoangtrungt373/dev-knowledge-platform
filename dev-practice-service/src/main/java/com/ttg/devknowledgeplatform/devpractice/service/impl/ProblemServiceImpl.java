package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
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
    private final ObjectMapper objectMapper;

    @Override
    public Problem create(ProblemCommands.Create command, String authorUuid) {
        validateTestCaseArity(command.parameters(), command.testCases());

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
                .methodName(command.methodName())
                .returnType(command.returnType())
                .build();
        replaceParameters(problem, command.parameters());
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

        // The grading contract (method name/return type/parameters) is frozen once a problem is
        // — and stays — PUBLISHED: a signature change invalidates every already-submitted
        // sourceCode's ability to compile/run and every existing TestCase's JSON encoding. Moving
        // the problem to DRAFT/ARCHIVED in this same request is the escape hatch (newStatus won't
        // be PUBLISHED then, so this check doesn't fire) — see this module's CLAUDE.md. testCases
        // are deliberately NOT part of this lock (see validateTestCaseArity below and its own
        // Javadoc for why they're allowed to change freely).
        if (ContentStatus.PUBLISHED.equals(prevStatus) && ContentStatus.PUBLISHED.equals(newStatus)) {
            Validator.isFalse(signatureChanged(problem, command), DevPracticeErrorCode.PROBLEM_SIGNATURE_LOCKED, id);
        }

        validateTestCaseArity(command.parameters(), command.testCases());

        problem.setMethodName(command.methodName());
        problem.setReturnType(command.returnType());
        problem.setStatus(newStatus);
        if (ContentStatus.PUBLISHED.equals(newStatus) && !ContentStatus.PUBLISHED.equals(prevStatus)
                && problem.getPublishedAt() == null) {
            problem.setPublishedAt(Instant.now());
        }

        replaceParameters(problem, command.parameters());
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

    /**
     * {@code true} if {@code command} would change {@code problem}'s current method name, return
     * type, or parameter list — compared against {@code problem}'s state as persisted (this must be
     * called before any of {@code problem}'s own signature fields are mutated).
     */
    private boolean signatureChanged(Problem problem, ProblemCommands.Update command) {
        if (!problem.getMethodName().equals(command.methodName())) {
            return true;
        }
        if (problem.getReturnType() != command.returnType()) {
            return true;
        }
        List<ProblemCommands.MethodParameterInput> current = problem.getParameters().stream()
                .sorted(Comparator.comparing(MethodParameter::getPosition))
                .map(p -> new ProblemCommands.MethodParameterInput(p.getName(), p.getType(), p.getPosition()))
                .toList();
        return !current.equals(command.parameters());
    }

    /**
     * Every {@code TestCase.input} must be a JSON array with exactly one element per parameter —
     * checked against {@code parameters} (the *final* parameter list about to be persisted, whether
     * or not it actually changed) on every create/update, regardless of publish status. Unlike the
     * signature lock above, test cases are never locked by publish state: adding, editing, or
     * removing a test case never invalidates already-submitted source code (only the signature
     * does), so there's no correctness reason to require unpublishing first — this arity check is
     * what actually protects against a test case silently drifting out of sync with the (possibly
     * frozen) signature once that looser policy is in place.
     */
    private void validateTestCaseArity(
            List<ProblemCommands.MethodParameterInput> parameters, List<ProblemCommands.TestCaseInput> testCases) {
        int expected = parameters.size();
        for (ProblemCommands.TestCaseInput testCase : testCases) {
            boolean valid;
            try {
                JsonNode parsed = objectMapper.readTree(testCase.input());
                valid = parsed.isArray() && parsed.size() == expected;
            } catch (JsonProcessingException e) {
                valid = false;
            }
            Validator.isTrue(valid, DevPracticeErrorCode.PROBLEM_TEST_CASE_ARITY_MISMATCH, testCase.input());
        }
    }

    /** Replace-all: clears the current parameter list (orphanRemoval deletes the old rows on save) and rebuilds it. */
    private void replaceParameters(Problem problem, List<ProblemCommands.MethodParameterInput> inputs) {
        problem.getParameters().clear();
        for (ProblemCommands.MethodParameterInput input : inputs) {
            MethodParameter parameter = MethodParameter.builder()
                    .problem(problem)
                    .name(input.name())
                    .type(input.type())
                    .position(input.position())
                    .build();
            problem.getParameters().add(parameter);
        }
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
