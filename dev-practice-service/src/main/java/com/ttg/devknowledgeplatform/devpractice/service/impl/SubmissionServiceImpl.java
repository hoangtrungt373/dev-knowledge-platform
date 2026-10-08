package com.ttg.devknowledgeplatform.devpractice.service.impl;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.event.SubmissionCreatedEvent;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Submission create(String userUuid, SubmissionCommands.Create command) {
        Problem problem = findProblem(command.problemId());
        // A draft/archived problem is treated as not found — same non-leaking posture as
        // ProblemService#getPublishedBySlug — a caller can only submit against a problem that is
        // actually visible to them.
        Validator.isTrue(ContentStatus.PUBLISHED.equals(problem.getStatus()),
                DevPracticeErrorCode.PROBLEM_NOT_FOUND, command.problemId());
        return saveAndJudge(problem, userUuid, command, SubmissionKind.USER, false);
    }

    @Override
    public Submission createReference(String adminUuid, SubmissionCommands.Create command, boolean publishOnAccept) {
        // No status check, unlike create: a reference run exists to verify a problem *before* it is
        // published. Admin-only by path (/api/v1/admin/**), not by anything checked here.
        return saveAndJudge(findProblem(command.problemId()), adminUuid, command, SubmissionKind.REFERENCE, publishOnAccept);
    }

    @Override
    @Transactional(readOnly = true)
    public Submission getSubmission(String userUuid, Integer id) {
        Submission submission = findSubmission(id);
        // A reference run is never reachable through the user API, even by the admin who made it.
        Validator.isTrue(submission.getKind() == SubmissionKind.USER && submission.getUserUuid().equals(userUuid),
                DevPracticeErrorCode.SUBMISSION_NOT_FOUND, id);
        return submission;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Submission> listSubmissions(String userUuid, Integer problemId, Pageable pageable) {
        return problemId != null
                ? submissionRepository.findByUserUuidAndKindAndProblem_Id(userUuid, SubmissionKind.USER, problemId, pageable)
                : submissionRepository.findByUserUuidAndKind(userUuid, SubmissionKind.USER, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Submission> listReferenceSubmissions(Integer problemId, Pageable pageable) {
        findProblem(problemId);
        return submissionRepository.findByProblem_IdAndKind(problemId, SubmissionKind.REFERENCE, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Submission getReferenceSubmission(Integer problemId, Integer submissionId) {
        Submission submission = findSubmission(submissionId);
        Validator.isTrue(submission.getKind() == SubmissionKind.REFERENCE
                        && submission.getProblem().getId().equals(problemId),
                DevPracticeErrorCode.SUBMISSION_NOT_FOUND, submissionId);
        return submission;
    }

    /** Persists a PENDING submission and hands it to the asynchronous judge. */
    private Submission saveAndJudge(
            Problem problem, String submitterUuid, SubmissionCommands.Create command, SubmissionKind kind,
            boolean publishOnAccept) {
        Submission saved = submissionRepository.save(Submission.builder()
                .problem(problem)
                .userUuid(submitterUuid)
                .language(command.language())
                .sourceCode(command.sourceCode())
                .status(SubmissionStatus.PENDING)
                .kind(kind)
                .publishOnAccept(publishOnAccept)
                .build());
        // Published now, but only actually delivered after this transaction commits — see
        // SubmissionJudgeEventListener's Javadoc for why it listens with phase = AFTER_COMMIT
        // rather than this reactor's usual @EventHandler (which would fire immediately here, still
        // inside this same not-yet-committed transaction).
        eventPublisher.publishEvent(new SubmissionCreatedEvent(saved.getId()));
        log.info("{} submitted {} solution {} for problem {}", submitterUuid, kind, saved.getId(), problem.getId());
        return saved;
    }

    private Problem findProblem(Integer problemId) {
        return Validator.notFound(problemRepository.findById(problemId), DevPracticeErrorCode.PROBLEM_NOT_FOUND, problemId);
    }

    private Submission findSubmission(Integer id) {
        return Validator.notFound(submissionRepository.findById(id), DevPracticeErrorCode.SUBMISSION_NOT_FOUND, id);
    }
}
