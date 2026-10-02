package com.ttg.devknowledgeplatform.devpractice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.event.SubmissionCreatedEvent;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;

/** The user vs. reference split: who may submit to a draft, and who can see what. */
class SubmissionServiceImplTest {

    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SubmissionServiceImpl service =
            new SubmissionServiceImpl(submissionRepository, problemRepository, eventPublisher);

    private final SubmissionCommands.Create command =
            new SubmissionCommands.Create(5, ProgrammingLanguage.JAVA, "class Solution {}");

    @Test
    void aUserCannotSubmitToADraft() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.DRAFT)));

        assertThatThrownBy(() -> service.create("user-1", command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_NOT_FOUND));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void anAdminCanSubmitAReferenceToADraftAndItIsJudged() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.DRAFT)));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(42);
            return s;
        });

        Submission created = service.createReference("admin-1", command);

        assertThat(created.getKind()).isEqualTo(SubmissionKind.REFERENCE);
        assertThat(created.getUserUuid()).isEqualTo("admin-1");
        verify(eventPublisher).publishEvent(new SubmissionCreatedEvent(42));
    }

    @Test
    void aReferenceSubmissionIsNotReachableThroughTheUserApiEvenByItsAuthor() {
        Submission reference = Submission.builder().kind(SubmissionKind.REFERENCE).userUuid("admin-1").build();
        when(submissionRepository.findById(42)).thenReturn(Optional.of(reference));

        assertThatThrownBy(() -> service.getSubmission("admin-1", 42))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.SUBMISSION_NOT_FOUND));
    }

    @Test
    void aReferenceSubmissionMustBelongToTheProblemInThePath() {
        Problem other = problem(ContentStatus.DRAFT);
        other.setId(6);
        when(submissionRepository.findById(42)).thenReturn(Optional.of(
                Submission.builder().kind(SubmissionKind.REFERENCE).problem(other).build()));

        assertThatThrownBy(() -> service.getReferenceSubmission(5, 42))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.SUBMISSION_NOT_FOUND));
    }

    private static Problem problem(ContentStatus status) {
        Problem problem = Problem.builder().title("Title").status(status).build();
        problem.setId(5);
        return problem;
    }
}
