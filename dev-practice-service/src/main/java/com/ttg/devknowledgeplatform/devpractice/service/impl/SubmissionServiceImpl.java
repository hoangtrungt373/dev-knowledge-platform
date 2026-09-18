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
        Problem problem = Validator.notFound(
                problemRepository.findById(command.problemId()), DevPracticeErrorCode.PROBLEM_NOT_FOUND, command.problemId());
        // A draft/archived problem is treated as not found — same non-leaking posture as
        // ProblemService#getPublishedBySlug — a caller can only submit against a problem that is
        // actually visible to them.
        Validator.isTrue(ContentStatus.PUBLISHED.equals(problem.getStatus()),
                DevPracticeErrorCode.PROBLEM_NOT_FOUND, command.problemId());

        Submission submission = Submission.builder()
                .problem(problem)
                .userUuid(userUuid)
                .language(command.language())
                .sourceCode(command.sourceCode())
                .status(SubmissionStatus.PENDING)
                .build();

        Submission saved = submissionRepository.save(submission);
        // Published now, but only actually delivered after this transaction commits — see
        // SubmissionJudgeEventListener's Javadoc for why it listens with phase = AFTER_COMMIT
        // rather than this reactor's usual @EventHandler (which would fire immediately here, still
        // inside this same not-yet-committed transaction).
        eventPublisher.publishEvent(new SubmissionCreatedEvent(saved.getId()));
        log.info("User {} submitted solution {} for problem {}", userUuid, saved.getId(), problem.getId());
        return saved;
    }

    @Override
    public Submission getSubmission(String userUuid, Integer id) {
        return resolveOwnedSubmission(userUuid, id);
    }

    @Override
    public Page<Submission> listSubmissions(String userUuid, Integer problemId, Pageable pageable) {
        return problemId != null
                ? submissionRepository.findByUserUuidAndProblem_Id(userUuid, problemId, pageable)
                : submissionRepository.findByUserUuid(userUuid, pageable);
    }

    private Submission resolveOwnedSubmission(String userUuid, Integer id) {
        Submission submission = Validator.notFound(
                submissionRepository.findById(id), DevPracticeErrorCode.SUBMISSION_NOT_FOUND, id);
        Validator.isTrue(submission.getUserUuid().equals(userUuid), DevPracticeErrorCode.SUBMISSION_NOT_FOUND, id);
        return submission;
    }
}
