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
    void anExplanationWithHandWrittenCodeIsRejected() {
        assertThatThrownBy(() -> service.create(7, new ProblemSolutionCommands.Create(
                "T", "Look:\n\n```java\nint x;\n```", List.of())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_SOLUTION_RAW_CODE_BLOCK));
        verify(solutionRepository, never()).save(any());
    }

    @Test
    void aPlaceholderMustMatchALinkedImplementationsLanguage() {
        stubRun(run(31, problem, SubmissionKind.REFERENCE, ProgrammingLanguage.JAVA));

        assertThatThrownBy(() -> service.create(7, new ProblemSolutionCommands.Create(
                "T", "{{code:PYTHON}}", List.of(31))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_SOLUTION_PLACEHOLDER_WITHOUT_CODE));
        verify(solutionRepository, never()).save(any());
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

    @Test
    void updateReplacesFieldsAndKeepsLinksThatStay() {
        Submission java = run(31, problem, SubmissionKind.REFERENCE, ProgrammingLanguage.JAVA);
        Submission python = run(32, problem, SubmissionKind.REFERENCE, ProgrammingLanguage.PYTHON);
        Submission newJava = run(33, problem, SubmissionKind.REFERENCE, ProgrammingLanguage.JAVA);
        stubRun(python);
        stubRun(newJava);
        ProblemSolution existing = approach(40, problem, 0);
        ProblemSolutionCode keptLink = existing.addCode(python);
        existing.addCode(java);
        when(solutionRepository.findById(40)).thenReturn(Optional.of(existing));

        ProblemSolution saved = service.update(7, 40, new ProblemSolutionCommands.Update(
                " Hash Set ", "Better.", List.of(32, 33)));

        assertThat(saved.getTitle()).isEqualTo("Hash Set");
        assertThat(saved.getExplanation()).isEqualTo("Better.");
        // Python's link object survives untouched; the old Java run is unlinked, the new one linked.
        assertThat(saved.getCodes()).contains(keptLink);
        assertThat(saved.getCodes()).extracting(c -> c.getSubmission().getId()).containsExactlyInAnyOrder(32, 33);
    }

    @Test
    void updateOfAnotherProblemsApproachIsNotFound() {
        when(solutionRepository.findById(40)).thenReturn(Optional.of(approach(40, problem(99), 0)));

        assertThatThrownBy(() -> service.update(7, 40, new ProblemSolutionCommands.Update("T", "E", List.of())))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(DevPracticeErrorCode.PROBLEM_SOLUTION_NOT_FOUND));
        verify(solutionRepository, never()).save(any());
    }

    @Test
    void deleteRenumbersTheRemainingApproaches() {
        ProblemSolution first = approach(40, problem, 0);
        ProblemSolution second = approach(41, problem, 1);
        ProblemSolution third = approach(42, problem, 2);
        when(solutionRepository.findById(41)).thenReturn(Optional.of(second));
        when(solutionRepository.findByProblem_IdOrderByPositionAscIdAsc(7)).thenReturn(List.of(first, second, third));

        service.delete(7, 41);

        verify(solutionRepository).delete(second);
        assertThat(first.getPosition()).isZero();
        assertThat(third.getPosition()).isEqualTo(1);
    }

    @Test
    void reorderAssignsPositionsInTheGivenOrder() {
        ProblemSolution a = approach(40, problem, 0);
        ProblemSolution b = approach(41, problem, 1);
        when(solutionRepository.findByProblem_IdOrderByPositionAscIdAsc(7)).thenReturn(List.of(a, b));

        List<ProblemSolution> ordered = service.reorder(7, List.of(41, 40));

        assertThat(ordered).containsExactly(b, a);
        assertThat(b.getPosition()).isZero();
        assertThat(a.getPosition()).isEqualTo(1);
    }

    @Test
    void reorderRejectsAListThatIsntExactlyTheProblemsApproaches() {
        when(solutionRepository.findByProblem_IdOrderByPositionAscIdAsc(7))
                .thenReturn(List.of(approach(40, problem, 0), approach(41, problem, 1)));

        for (List<Integer> stale : List.of(List.of(40), List.of(40, 41, 99), List.of(40, 40))) {
            assertThatThrownBy(() -> service.reorder(7, stale))
                    .as("%s", stale)
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(DevPracticeErrorCode.PROBLEM_SOLUTION_ORDER_MISMATCH));
        }
    }

    @Test
    void adminListWorksForAnyStatusButNeedsAnExistingProblem() {
        List<ProblemSolution> approaches = List.of(approach(40, problem, 0));
        when(solutionRepository.findByProblem_IdOrderByPositionAscIdAsc(7)).thenReturn(approaches);

        assertThat(service.listForAdmin(7)).isSameAs(approaches);
        verify(problemService).getById(7);
    }

    private static ProblemSolution approach(int id, Problem problem, int position) {
        ProblemSolution solution = ProblemSolution.builder().problem(problem).title("A").explanation("E").position(position).build();
        solution.setId(id);
        return solution;
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
