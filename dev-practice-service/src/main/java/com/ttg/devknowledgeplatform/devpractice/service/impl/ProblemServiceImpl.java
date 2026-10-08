package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTagAssignment;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.harness.SignatureNameValidator;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
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
    private final SubmissionRepository submissionRepository;
    private final ProblemTagRepository problemTagRepository;
    private final SignatureNameValidator signatureNameValidator;
    private final SlugService slugService;
    private final ObjectMapper objectMapper;

    @Override
    public Problem create(ProblemCommands.Create command, String authorUuid) {
        validateSignatureNames(command.methodName(), command.parameters());
        validateTestCaseArity(command.parameters(), command.testCases());

        ContentStatus status = command.status() != null ? command.status() : ContentStatus.DRAFT;
        // A problem that doesn't exist yet can't have a reference submission, so it can never be
        // created straight into PUBLISHED: create it as a draft, verify it, then publish.
        Validator.isFalse(ContentStatus.PUBLISHED.equals(status), DevPracticeErrorCode.PROBLEM_NOT_VERIFIED);

        String slug = slugService.generateUniqueSlug(
                command.title(), problemRepository::existsBySlug, DevPracticeErrorCode.PROBLEM_SLUG_CONFLICT);

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
        replaceTags(problem, command.tagIds() == null ? Set.of() : command.tagIds());

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
        // be PUBLISHED then, so this check doesn't fire) — see this module's CLAUDE.md. Test data
        // isn't covered by this lock, but since DKP-0056 a test-data change while published is
        // stopped by the verification check further down instead (it bumps contractVersion, which
        // no existing reference submission matches). This check stays for its clearer message.
        if (ContentStatus.PUBLISHED.equals(prevStatus) && ContentStatus.PUBLISHED.equals(newStatus)) {
            Validator.isFalse(signatureChanged(problem, command), DevPracticeErrorCode.PROBLEM_SIGNATURE_LOCKED, id);
        }

        validateSignatureNames(command.methodName(), command.parameters());
        validateTestCaseArity(command.parameters(), command.testCases());

        // Compared against the persisted state, so it must run before any field below is mutated.
        boolean contractChanged = signatureChanged(problem, command) || testDataChanged(problem, command);

        problem.setMethodName(command.methodName());
        problem.setReturnType(command.returnType());
        problem.setStatus(newStatus);
        if (ContentStatus.PUBLISHED.equals(newStatus) && !ContentStatus.PUBLISHED.equals(prevStatus)
                && problem.getPublishedAt() == null) {
            problem.setPublishedAt(Instant.now());
        }

        replaceParameters(problem, command.parameters());
        replaceTestCases(problem, command.testCases());
        if (command.tagIds() != null) {
            replaceTags(problem, command.tagIds());
        }

        if (contractChanged) {
            problem.setContractVersion(problem.getContractVersion() + 1);
        }
        // Publishing — or keeping a problem published through a contract change — requires an
        // ACCEPTED reference at the (possibly just bumped) contract version. A contract change while
        // published therefore always fails here: no reference can have been judged against a version
        // that only exists from this save on. The escape hatch is the same as the signature lock's:
        // save as DRAFT (the version bumps), run a reference, then publish. Throwing rolls the whole
        // update back (rollbackFor = Throwable), bump included.
        if (ContentStatus.PUBLISHED.equals(newStatus)
                && (!ContentStatus.PUBLISHED.equals(prevStatus) || contractChanged)) {
            Validator.isTrue(isVerified(problem), DevPracticeErrorCode.PROBLEM_NOT_VERIFIED);
        }

        Problem updated = problemRepository.save(problem);
        log.info("Updated problem {}", id);
        return updated;
    }

    @Override
    public void delete(Integer id) {
        Problem problem = findById(id);
        // FK_SUBMISSION_PROBLEM deliberately has no ON DELETE CASCADE: a user's submission is their
        // own history, not part of the problem the way test cases/parameters are. Refuse cleanly here
        // instead of letting the FK violation surface as a 500; ARCHIVED is the way to retire a
        // problem people have already attempted. Only USER submissions count — an admin's reference
        // runs are part of authoring the problem, so they're deleted with it (explicitly, since the
        // FK doesn't cascade) rather than blocking the delete.
        long submissions = submissionRepository.countByProblem_IdAndKind(id, SubmissionKind.USER);
        Validator.isTrue(submissions == 0, DevPracticeErrorCode.PROBLEM_HAS_SUBMISSIONS, submissions);
        submissionRepository.deleteByProblem_IdAndKind(id, SubmissionKind.REFERENCE);
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
    public Page<Problem> list(Pageable pageable, Difficulty difficulty, ContentStatus status, String q, Set<Integer> tagIds) {
        return problemRepository.findAll(ProblemSpecification.withFilters(difficulty, status, q, tagIds), pageable);
    }

    @Override
    public boolean publishIfVerified(Integer problemId, Integer judgedContractVersion) {
        Problem problem = getById(problemId);
        if (!ContentStatus.DRAFT.equals(problem.getStatus())) {
            log.info("Publish-on-accept skipped for problem {}: it is {} now, not DRAFT", problemId, problem.getStatus());
            return false;
        }
        // Re-checked here rather than trusted from the caller: the problem may have been edited
        // (version bumped) between the run being judged and this transaction.
        if (!problem.getContractVersion().equals(judgedContractVersion) || !isVerified(problem)) {
            log.info("Publish-on-accept skipped for problem {}: judged at contract v{}, current is v{}",
                    problemId, judgedContractVersion, problem.getContractVersion());
            return false;
        }
        problem.setStatus(ContentStatus.PUBLISHED);
        if (problem.getPublishedAt() == null) {
            problem.setPublishedAt(Instant.now());
        }
        problemRepository.save(problem);
        log.info("Published problem {} after its reference solution was accepted", problemId);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVerified(Problem problem) {
        return problem.getId() != null && submissionRepository.existsByProblem_IdAndKindAndStatusAndContractVersion(
                problem.getId(), SubmissionKind.REFERENCE, SubmissionStatus.ACCEPTED, problem.getContractVersion());
    }

    /**
     * {@code true} if {@code command} would change any test case's input or expected output, or the
     * number/order of test cases — compared against the persisted set. The {@code sample} flag is
     * deliberately ignored: showing a case as an example doesn't change what a correct solution must
     * return. Order counts (conservatively — reordering bumps the version too) because comparing as
     * an ordered list is simple and exact, and a reorder is rare.
     */
    private boolean testDataChanged(Problem problem, ProblemCommands.Update command) {
        List<List<String>> current = problem.getTestCases().stream()
                .map(t -> List.of(t.getInput().strip(), t.getExpectedOutput().strip()))
                .toList();
        List<List<String>> incoming = command.testCases().stream()
                .map(t -> List.of(t.input().strip(), t.expectedOutput().strip()))
                .toList();
        return !current.equals(incoming);
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

    /** Delegates to {@link SignatureNameValidator} — every name must compile in every supported language. */
    private void validateSignatureNames(String methodName, List<ProblemCommands.MethodParameterInput> parameters) {
        signatureNameValidator.validate(methodName,
                parameters.stream().map(ProblemCommands.MethodParameterInput::name).toList());
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
            // Two args, so this binds to the template-args overload — a lone String argument would
            // bind to Validator's isTrue(..., String message) and become the whole message instead.
            Validator.isTrue(valid, DevPracticeErrorCode.PROBLEM_TEST_CASE_ARITY_MISMATCH, testCase.input(), expected);
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

    /**
     * Makes {@code problem}'s tags exactly {@code tagIds} — by diffing, not clear-and-rebuild like
     * {@link #replaceTestCases}. The difference matters because of UK_PROBLEM_TAG_ASSIGNMENT_PAIR:
     * Hibernate's flush runs INSERTs before orphan-removal DELETEs, so clearing and re-adding a tag
     * the problem already had would insert the duplicate (problem, tag) row first and violate the
     * constraint. Keeping unchanged assignments also avoids rewriting rows (and their audit columns)
     * on every save.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException
     *         {@code PROBLEM_TAG_NOT_FOUND} if any id doesn't exist
     */
    private void replaceTags(Problem problem, Set<Integer> tagIds) {
        // stream().anyMatch, not contains(null): immutable sets (Set.of) throw NPE on contains(null).
        Validator.isFalse(tagIds.stream().anyMatch(Objects::isNull), DevPracticeErrorCode.PROBLEM_TAG_NOT_FOUND);
        List<ProblemTag> tags = problemTagRepository.findAllById(tagIds);
        Validator.isTrue(tags.size() == tagIds.size(), DevPracticeErrorCode.PROBLEM_TAG_NOT_FOUND, tagIds);

        problem.getTagAssignments().removeIf(a -> !tagIds.contains(a.getProblemTag().getId()));
        Set<Integer> kept = problem.getTagAssignments().stream()
                .map(a -> a.getProblemTag().getId())
                .collect(Collectors.toSet());
        tags.stream()
                .filter(tag -> !kept.contains(tag.getId()))
                .forEach(tag -> problem.getTagAssignments().add(
                        ProblemTagAssignment.builder().problem(problem).problemTag(tag).build()));
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
