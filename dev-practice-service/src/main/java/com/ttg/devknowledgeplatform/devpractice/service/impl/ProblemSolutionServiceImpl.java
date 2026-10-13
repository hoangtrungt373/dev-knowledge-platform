package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
 * Editorial approaches. Validation lives here (not in the seeder) so the future admin endpoints get
 * the same rules for free: every linked run is one of <em>this</em> problem's {@code REFERENCE}
 * submissions, at most one per language, and the explanation passes {@link CodePlaceholders#validate}
 * (no hand-written code, placeholders only for implemented languages). A run's verdict is
 * deliberately <em>not</em> required at link time — the seeder links runs the judge hasn't finished
 * yet — because the read side ({@link ProblemSolution#verifiedCode()}) only ever shows accepted,
 * current ones.
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
    public ProblemSolution create(Integer problemId, ProblemSolutionCommands.Create command) {
        Problem problem = problemService.getById(problemId);
        ProblemSolution solution = ProblemSolution.builder()
                .problem(problem)
                .title(command.title().trim())
                .explanation(command.explanation().strip())
                .position(problemSolutionRepository.countByProblem_Id(problemId))
                .build();

        Set<ProgrammingLanguage> languages = EnumSet.noneOf(ProgrammingLanguage.class);
        for (Integer submissionId : command.referenceSubmissionIds()) {
            Submission run = submissionRepository.findById(submissionId)
                    .filter(s -> s.getKind() == SubmissionKind.REFERENCE
                            && Objects.equals(s.getProblem().getId(), problemId))
                    .orElse(null);
            // (Object) cast: a lone Integer is fine for the varargs overload, but keep it explicit so a
            // future String argument can't silently bind to the (..., String message) overload.
            Validator.isTrue(run != null, DevPracticeErrorCode.PROBLEM_SOLUTION_INVALID_REFERENCE, (Object) submissionId);
            Validator.isTrue(languages.add(run.getLanguage()),
                    DevPracticeErrorCode.PROBLEM_SOLUTION_DUPLICATE_LANGUAGE, (Object) run.getLanguage().name());
            solution.addCode(run);
        }
        CodePlaceholders.validate(solution.getExplanation(), languages);

        ProblemSolution saved = problemSolutionRepository.save(solution);
        log.info("Added solution '{}' to problem {} with {} implementation(s)", saved.getTitle(), problemId, languages.size());
        return saved;
    }
}
