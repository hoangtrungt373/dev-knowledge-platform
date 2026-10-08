package com.ttg.devknowledgeplatform.devpractice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTagAssignment;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaLanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaScriptLanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.harness.PythonLanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.harness.SignatureNameValidator;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemCommands;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

/**
 * Covers {@link ProblemServiceImpl}'s guards that run before anything is persisted: the
 * delete-with-submissions refusal, signature-name validation, and the arity error's message.
 */
class ProblemServiceImplTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final ProblemTagRepository problemTagRepository = mock(ProblemTagRepository.class);
    private final SlugService slugService = mock(SlugService.class);
    private final SignatureNameValidator nameValidator = new SignatureNameValidator(List.of(
            new JavaLanguageHarness(), new PythonLanguageHarness(), new JavaScriptLanguageHarness()));
    private final ProblemServiceImpl service = new ProblemServiceImpl(
            problemRepository, submissionRepository, problemTagRepository, nameValidator, slugService, new ObjectMapper());

    @Test
    void refusesToDeleteAProblemThatHasSubmissions() {
        Problem problem = Problem.builder().title("Two Sum").build();
        when(problemRepository.findById(7)).thenReturn(Optional.of(problem));
        when(submissionRepository.countByProblem_IdAndKind(7, SubmissionKind.USER)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(7))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_HAS_SUBMISSIONS))
                .hasMessageContaining("3 submission(s)")
                .hasMessageContaining("archive it instead");
        verify(problemRepository, never()).delete(problem);
    }

    @Test
    void deletesAProblemWithNoSubmissions() {
        Problem problem = Problem.builder().title("Two Sum").build();
        when(problemRepository.findById(7)).thenReturn(Optional.of(problem));
        when(submissionRepository.countByProblem_IdAndKind(7, SubmissionKind.USER)).thenReturn(0L);

        service.delete(7);

        // Reference runs are part of authoring the problem — removed with it, not a blocker.
        verify(submissionRepository).deleteByProblem_IdAndKind(7, SubmissionKind.REFERENCE);
        verify(problemRepository).delete(problem);
    }

    // ── Publishing requires an ACCEPTED reference at the current contract version ──────────────

    @Test
    void cannotCreateAProblemStraightIntoPublished() {
        ProblemCommands.Create command = new ProblemCommands.Create("Title", "Description", Difficulty.EASY,
                ContentStatus.PUBLISHED, "solve", ParamType.INT, List.of(param("a", 0)), List.of(testCase("[1]")), null);

        assertThatThrownBy(() -> service.create(command, "author-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_NOT_VERIFIED));
        verify(problemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishingADraftWithoutAnAcceptedReferenceIsRefused() {
        Problem problem = storedProblem(ContentStatus.DRAFT, 3);
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));

        assertThatThrownBy(() -> service.update(5, update(ContentStatus.PUBLISHED, "[1]", "1", true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_NOT_VERIFIED));
    }

    @Test
    void publishingADraftWithAnAcceptedReferenceAtTheCurrentVersionSucceeds() {
        Problem problem = storedProblem(ContentStatus.DRAFT, 3);
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));
        when(problemRepository.save(problem)).thenReturn(problem);
        when(submissionRepository.existsByProblem_IdAndKindAndStatusAndContractVersion(
                5, SubmissionKind.REFERENCE, SubmissionStatus.ACCEPTED, 3)).thenReturn(true);

        service.update(5, update(ContentStatus.PUBLISHED, "[1]", "1", true));

        assertThat(problem.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(problem.getContractVersion()).isEqualTo(3);
    }

    @Test
    void changingTestDataWhilePublishedIsRefusedEvenIfTheOldVersionWasVerified() {
        Problem problem = storedProblem(ContentStatus.PUBLISHED, 3);
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));
        // Verified at version 3 — but the edit bumps the contract to 4.
        when(submissionRepository.existsByProblem_IdAndKindAndStatusAndContractVersion(
                5, SubmissionKind.REFERENCE, SubmissionStatus.ACCEPTED, 3)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5, update(ContentStatus.PUBLISHED, "[1]", "2", true)))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_NOT_VERIFIED));
        verify(submissionRepository).existsByProblem_IdAndKindAndStatusAndContractVersion(
                5, SubmissionKind.REFERENCE, SubmissionStatus.ACCEPTED, 4);
    }

    @Test
    void savingAsDraftWithChangedTestDataBumpsTheContractVersion() {
        Problem problem = storedProblem(ContentStatus.PUBLISHED, 3);
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));
        when(problemRepository.save(problem)).thenReturn(problem);

        service.update(5, update(ContentStatus.DRAFT, "[2]", "2", true));

        assertThat(problem.getContractVersion()).isEqualTo(4);
        assertThat(problem.getStatus()).isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void togglingOnlyTheSampleFlagKeepsAPublishedProblemVerified() {
        Problem problem = storedProblem(ContentStatus.PUBLISHED, 3);
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));
        when(problemRepository.save(problem)).thenReturn(problem);

        service.update(5, update(ContentStatus.PUBLISHED, "[1]", "1", false));

        assertThat(problem.getContractVersion()).isEqualTo(3);
        // Staying published with an unchanged contract never re-checks verification.
        verify(submissionRepository, never()).existsByProblem_IdAndKindAndStatusAndContractVersion(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishOnAcceptPublishesAVerifiedDraft() {
        Problem problem = storedProblem(ContentStatus.DRAFT, 3);
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));
        when(submissionRepository.existsByProblem_IdAndKindAndStatusAndContractVersion(
                5, SubmissionKind.REFERENCE, SubmissionStatus.ACCEPTED, 3)).thenReturn(true);

        assertThat(service.publishIfVerified(5, 3)).isTrue();
        assertThat(problem.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(problem.getPublishedAt()).isNotNull();
    }

    @Test
    void publishOnAcceptLeavesADraftEditedDuringJudgingAlone() {
        // Judged at v3, but the admin saved a test-data change (v4) while it was running.
        Problem problem = storedProblem(ContentStatus.DRAFT, 4);
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));

        assertThat(service.publishIfVerified(5, 3)).isFalse();
        assertThat(problem.getStatus()).isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void publishOnAcceptNeverRepublishesAnArchivedProblem() {
        Problem problem = storedProblem(ContentStatus.ARCHIVED, 3);
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));

        assertThat(service.publishIfVerified(5, 3)).isFalse();
        assertThat(problem.getStatus()).isEqualTo(ContentStatus.ARCHIVED);
    }

    /** A persisted problem: signature solve(int a) -> int, one test case [1] -> 1. */
    private static Problem storedProblem(ContentStatus status, int contractVersion) {
        Problem problem = Problem.builder().title("Title").description("Description").difficulty(Difficulty.EASY)
                .status(status).methodName("solve").returnType(ParamType.INT).contractVersion(contractVersion).build();
        problem.setId(5);
        problem.getParameters().add(MethodParameter.builder().problem(problem).name("a").type(ParamType.INT).position(0).build());
        problem.getTestCases().add(TestCase.builder().problem(problem).input("[1]").expectedOutput("1").sample(true).build());
        return problem;
    }

    private static ProblemCommands.Update update(ContentStatus status, String input, String expected, boolean sample) {
        return new ProblemCommands.Update("Title", "Description", Difficulty.EASY, status, "solve", ParamType.INT,
                List.of(param("a", 0)), List.of(new ProblemCommands.TestCaseInput(input, expected, sample)), null);
    }

    @Test
    void rejectsAReservedParameterNameOnCreate() {
        ProblemCommands.Create command = create("solve", List.of(param("def", 0)), List.of(testCase("[1]")));

        assertThatThrownBy(() -> service.create(command, "author-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("'def' can't be used as a parameter name: it is reserved in PYTHON");
        verify(problemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void arityErrorNamesTheOffendingInputAndExpectedCount() {
        ProblemCommands.Create command = create("solve",
                List.of(param("a", 0), param("b", 1)), List.of(testCase("[1, 2, 3]")));

        assertThatThrownBy(() -> service.create(command, "author-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Test case input [1, 2, 3] must be a JSON array with exactly 2 value(s), one per parameter");
    }

    @Test
    void rejectsAnUnknownTagIdOnCreate() {
        when(problemTagRepository.findAllById(Set.of(1, 99))).thenReturn(List.of(tag(1, "Array")));
        ProblemCommands.Create command = new ProblemCommands.Create("Title", "Description", Difficulty.EASY, null,
                "solve", ParamType.INT, List.of(param("a", 0)), List.of(testCase("[1]")), Set.of(1, 99));

        assertThatThrownBy(() -> service.create(command, "author-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_TAG_NOT_FOUND));
    }

    @Test
    void updateDiffsTagsInsteadOfRebuildingThem() {
        // Clear-and-rebuild would insert a duplicate (problem, tag) row before orphan removal deletes
        // the old one and trip UK_PROBLEM_TAG_ASSIGNMENT_PAIR — so an unchanged tag must keep its
        // exact assignment object.
        ProblemTag array = tag(1, "Array");
        ProblemTag math = tag(2, "Math");
        ProblemTag stack = tag(3, "Stack");
        Problem problem = Problem.builder().title("Title").methodName("solve").returnType(ParamType.INT)
                .status(ContentStatus.DRAFT).build();
        ProblemTagAssignment keptAssignment = ProblemTagAssignment.builder().problem(problem).problemTag(array).build();
        problem.getTagAssignments().add(keptAssignment);
        problem.getTagAssignments().add(ProblemTagAssignment.builder().problem(problem).problemTag(math).build());
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));
        when(problemTagRepository.findAllById(Set.of(1, 3))).thenReturn(List.of(array, stack));
        when(problemRepository.save(problem)).thenReturn(problem);

        service.update(5, new ProblemCommands.Update("Title", "Description", Difficulty.EASY, null,
                "solve", ParamType.INT, List.of(param("a", 0)), List.of(testCase("[1]")), Set.of(1, 3)));

        assertThat(problem.getTagAssignments()).hasSize(2).contains(keptAssignment);
        assertThat(problem.getTagAssignments()).extracting(a -> a.getProblemTag().getName())
                .containsExactlyInAnyOrder("Array", "Stack");
    }

    @Test
    void updateWithNullTagIdsLeavesTagsUnchanged() {
        Problem problem = Problem.builder().title("Title").methodName("solve").returnType(ParamType.INT)
                .status(ContentStatus.DRAFT).build();
        problem.getTagAssignments().add(
                ProblemTagAssignment.builder().problem(problem).problemTag(tag(1, "Array")).build());
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem));
        when(problemRepository.save(problem)).thenReturn(problem);

        service.update(5, new ProblemCommands.Update("Title", "Description", Difficulty.EASY, null,
                "solve", ParamType.INT, List.of(param("a", 0)), List.of(testCase("[1]")), null));

        assertThat(problem.getTagAssignments()).hasSize(1);
        verify(problemTagRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    private static ProblemTag tag(int id, String name) {
        ProblemTag tag = ProblemTag.builder().name(name).slug(name.toLowerCase()).build();
        tag.setId(id);
        return tag;
    }

    private static ProblemCommands.Create create(String methodName, List<ProblemCommands.MethodParameterInput> params,
            List<ProblemCommands.TestCaseInput> testCases) {
        return new ProblemCommands.Create("Title", "Description", Difficulty.EASY, null,
                methodName, ParamType.INT, params, testCases, null);
    }

    private static ProblemCommands.MethodParameterInput param(String name, int position) {
        return new ProblemCommands.MethodParameterInput(name, ParamType.INT, position);
    }

    private static ProblemCommands.TestCaseInput testCase(String input) {
        return new ProblemCommands.TestCaseInput(input, "1", true);
    }
}
