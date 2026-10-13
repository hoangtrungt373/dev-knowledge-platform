package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.devpractice.editorial.CodePlaceholders;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemSolutionRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Editorial approaches. Validation lives here (not in the seeder or the controller) so every writer —
 * the seeder and the admin endpoints — gets the same rules: every linked run is one of <em>this</em>
 * problem's {@code REFERENCE} submissions, at most one per language, and the explanation passes
 * {@link CodePlaceholders#validate} (no hand-written code, placeholders only for implemented
 * languages). A run's verdict is deliberately <em>not</em> required at link time — the seeder links
 * runs the judge hasn't finished yet — because the read side ({@link ProblemSolution#verifiedCode()})
 * only ever shows accepted, current ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Throwable.class)
public class ProblemSolutionServiceImpl implements ProblemSolutionService {

    private final ProblemSolutionRepository problemSolutionRepository;
    private final SubmissionRepository submissionRepository;
    private final ProblemService problemService;

    @Override
    @Transactional(readOnly = true)
    public List<ProblemSolution> listPublishedBySlug(String slug) {
        Problem problem = problemService.getPublishedBySlug(slug);
        return problemSolutionRepository.findByProblem_IdOrderByPositionAscIdAsc(problem.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemSolution> listForAdmin(Integer problemId) {
        problemService.getById(problemId); // 404 for an unknown problem rather than an empty list
        return problemSolutionRepository.findByProblem_IdOrderByPositionAscIdAsc(problemId);
    }

    @Override
    public ProblemSolution create(Integer problemId, ProblemSolutionCommands.Create command) {
        Problem problem = problemService.getById(problemId);
        ProblemSolution solution = ProblemSolution.builder()
                .problem(problem)
                .title(command.title().trim())
                .explanation(command.explanation().strip())
                .position(problemSolutionRepository.countByProblem_Id(problemId))
                .build();
        solution.replaceCodes(validatedRuns(problemId, solution.getExplanation(), command.referenceSubmissionIds()));

        ProblemSolution saved = problemSolutionRepository.save(solution);
        log.info("Added solution '{}' to problem {} with {} implementation(s)", saved.getTitle(), problemId, saved.getCodes().size());
        return saved;
    }

    @Override
    public ProblemSolution update(Integer problemId, Integer solutionId, ProblemSolutionCommands.Update command) {
        ProblemSolution solution = findInProblem(problemId, solutionId);
        solution.setTitle(command.title().trim());
        solution.setExplanation(command.explanation().strip());
        solution.replaceCodes(validatedRuns(problemId, solution.getExplanation(), command.referenceSubmissionIds()));

        ProblemSolution saved = problemSolutionRepository.save(solution);
        log.info("Updated solution {} of problem {}", solutionId, problemId);
        return saved;
    }

    @Override
    public void delete(Integer problemId, Integer solutionId) {
        ProblemSolution solution = findInProblem(problemId, solutionId);
        problemSolutionRepository.delete(solution);
        // Close the gap so positions stay 0..n-1 — what the next create appends after.
        List<ProblemSolution> remaining = new ArrayList<>(problemSolutionRepository.findByProblem_IdOrderByPositionAscIdAsc(problemId));
        remaining.remove(solution);
        renumber(remaining);
        log.info("Deleted solution {} of problem {}", solutionId, problemId);
    }

    @Override
    public List<ProblemSolution> reorder(Integer problemId, List<Integer> orderedIds) {
        problemService.getById(problemId);
        List<ProblemSolution> current = problemSolutionRepository.findByProblem_IdOrderByPositionAscIdAsc(problemId);
        Map<Integer, ProblemSolution> byId = current.stream()
                .collect(Collectors.toMap(ProblemSolution::getId, Function.identity()));
        // Exactly the problem's approaches, each once — a stale admin page must not drop or invent one.
        Validator.isTrue(orderedIds.size() == byId.size() && new HashSet<>(orderedIds).equals(byId.keySet()),
                DevPracticeErrorCode.PROBLEM_SOLUTION_ORDER_MISMATCH);
        List<ProblemSolution> ordered = orderedIds.stream().map(byId::get).toList();
        renumber(ordered);
        return ordered;
    }

    /**
     * The runs named by {@code submissionIds}, checked to be this problem's REFERENCE runs, one per
     * language, and that {@code explanation}'s placeholders only name those languages.
     */
    private List<Submission> validatedRuns(Integer problemId, String explanation, List<Integer> submissionIds) {
        Set<ProgrammingLanguage> languages = EnumSet.noneOf(ProgrammingLanguage.class);
        List<Submission> runs = new ArrayList<>();
        for (Integer submissionId : submissionIds) {
            Submission run = submissionRepository.findById(submissionId)
                    .filter(s -> s.getKind() == SubmissionKind.REFERENCE
                            && Objects.equals(s.getProblem().getId(), problemId))
                    .orElse(null);
            // (Object) cast: a lone Integer is fine for the varargs overload, but keep it explicit so a
            // future String argument can't silently bind to the (..., String message) overload.
            Validator.isTrue(run != null, DevPracticeErrorCode.PROBLEM_SOLUTION_INVALID_REFERENCE, (Object) submissionId);
            Validator.isTrue(languages.add(run.getLanguage()),
                    DevPracticeErrorCode.PROBLEM_SOLUTION_DUPLICATE_LANGUAGE, (Object) run.getLanguage().name());
            runs.add(run);
        }
        CodePlaceholders.validate(explanation, languages);
        return runs;
    }

    /** One of {@code problemId}'s approaches — another problem's id is "not found", never touched. */
    private ProblemSolution findInProblem(Integer problemId, Integer solutionId) {
        return Validator.notFound(problemSolutionRepository.findById(solutionId)
                        .filter(s -> Objects.equals(s.getProblem().getId(), problemId)),
                DevPracticeErrorCode.PROBLEM_SOLUTION_NOT_FOUND, solutionId);
    }

    private static void renumber(List<ProblemSolution> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
        }
    }
}
