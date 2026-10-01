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
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTagAssignment;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
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
        when(submissionRepository.countByProblem_Id(7)).thenReturn(3L);

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
        when(submissionRepository.countByProblem_Id(7)).thenReturn(0L);

        service.delete(7);

        verify(problemRepository).delete(problem);
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
