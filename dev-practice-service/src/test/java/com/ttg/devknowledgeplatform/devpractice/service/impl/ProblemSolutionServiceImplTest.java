package com.ttg.devknowledgeplatform.devpractice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolutionCode;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemSolutionRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionCommands;

/** Approach creation rules (this problem's reference runs only, one per language) and the published-only read. */
class ProblemSolutionServiceImplTest {

    private final ProblemSolutionRepository solutionRepository = mock(ProblemSolutionRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final ProblemService problemService = mock(ProblemService.class);
    private final ProblemSolutionServiceImpl service =
            new ProblemSolutionServiceImpl(solutionRepository, submissionRepository, problemService);

    private final Problem problem = problem(7);

    @BeforeEach
    void setUp() {
        when(problemService.getById(7)).thenReturn(problem);
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void appendsAnApproachLinkingItsReferenceRuns() {
        stubRun(run(31, problem, SubmissionKind.REFERENCE, ProgrammingLanguage.JAVA));
        stubRun(run(32, problem, SubmissionKind.REFERENCE, ProgrammingLanguage.PYTHON));
        when(solutionRepository.countByProblem_Id(7)).thenReturn(2);

        ProblemSolution saved = service.create(7, new ProblemSolutionCommands.Create(
                "  Hash Set ", "\nExplanation\n", List.of(31, 32)));

        assertThat(saved.getTitle()).isEqualTo("Hash Set");
        assertThat(saved.getExplanation()).isEqualTo("Explanation");
        assertThat(saved.getPosition()).as("after the two existing approaches").isEqualTo(2);
        assertThat(saved.getProblem()).isSameAs(problem);
        assertThat(saved.getCodes()).extracting(ProblemSolutionCode::getSubmission)
                .extracting(Submission::getId).containsExactly(31, 32);
        assertThat(saved.getCodes()).allSatisfy(code -> assertThat(code.getSolution()).isSameAs(saved));
    }

    @Test
    void anExplanationOnlyApproachIsAllowed() {
        ProblemSolution saved = service.create(7, new ProblemSolutionCommands.Create("Brute force", "Too slow, but…", List.of()));

        assertThat(saved.getCodes()).isEmpty();
    }

    @Test
    void anotherProblemsRunIsRejected() {
        stubRun(run(31, problem(99), SubmissionKind.REFERENCE, ProgrammingLanguage.JAVA));

        assertRejected(List.of(31), DevPracticeErrorCode.PROBLEM_SOLUTION_INVALID_REFERENCE);
    }

    @Test
    void aLearnersOwnSubmissionIsRejected() {
        stubRun(run(31, problem, SubmissionKind.USER, ProgrammingLanguage.JAVA));

        assertRejected(List.of(31), DevPracticeErrorCode.PROBLEM_SOLUTION_INVALID_REFERENCE);
    }

    @Test
    void anUnknownRunIsRejected() {
        when(submissionRepository.findById(31)).thenReturn(Optional.empty());

        assertRejected(List.of(31), DevPracticeErrorCode.PROBLEM_SOLUTION_INVALID_REFERENCE);
    }

    @Test
    void twoRunsInOneLanguageAreRejected() {
        stubRun(run(31, problem, SubmissionKind.REFERENCE, ProgrammingLanguage.JAVA));
        stubRun(run(32, problem, SubmissionKind.REFERENCE, ProgrammingLanguage.JAVA));

        assertRejected(List.of(31, 32), DevPracticeErrorCode.PROBLEM_SOLUTION_DUPLICATE_LANGUAGE);
    }

    @Test
    void listsAPublishedProblemsApproachesInOrder() {
        when(problemService.getPublishedBySlug("contains-duplicate")).thenReturn(problem);
        List<ProblemSolution> approaches = List.of(ProblemSolution.builder().title("A").build());
        when(solutionRepository.findByProblem_IdOrderByPositionAscIdAsc(7)).thenReturn(approaches);

        assertThat(service.listPublishedBySlug("contains-duplicate")).isSameAs(approaches);
    }

    @Test
    void anUnpublishedProblemHasNoVisibleApproaches() {
        BusinessException notFound = new BusinessException(DevPracticeErrorCode.PROBLEM_NOT_FOUND, "draft");
        when(problemService.getPublishedBySlug("draft")).thenThrow(notFound);

        assertThatThrownBy(() -> service.listPublishedBySlug("draft")).isSameAs(notFound);
        verify(solutionRepository, never()).findByProblem_IdOrderByPositionAscIdAsc(any());
    }

    private void assertRejected(List<Integer> runIds, DevPracticeErrorCode expected) {
        assertThatThrownBy(() -> service.create(7, new ProblemSolutionCommands.Create("T", "E", runIds)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        verify(solutionRepository, never()).save(any());
    }

    private void stubRun(Submission run) {
        when(submissionRepository.findById(run.getId())).thenReturn(Optional.of(run));
    }

    private static Submission run(int id, Problem problem, SubmissionKind kind, ProgrammingLanguage language) {
        Submission run = Submission.builder().problem(problem).kind(kind).language(language).build();
        run.setId(id);
        return run;
    }

    private static Problem problem(int id) {
        Problem problem = Problem.builder().build();
        problem.setId(id);
        return problem;
    }
}
