package com.ttg.devknowledgeplatform.devpractice.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One user's code submission against a {@link Problem}.
 *
 * <p>{@code userUuid} is a plain column (the submitting caller's Keycloak {@code sub} claim),
 * never a {@code User} foreign key — same "Option C" shape as {@code task-service}'s
 * {@code Task.ownerUuid}/{@code content-service}'s {@code ContentItem.authorUuid} (see root
 * {@code CLAUDE.md}'s Security section).
 *
 * <p>Judged asynchronously by {@code event.SubmissionJudgeEventListener} after creation — a
 * submission is created and returned to the caller as {@link SubmissionStatus#PENDING}
 * immediately, then transitions to its final status once every {@link TestCase} has been run
 * through Judge0 (via {@code judge.JudgeClient}, all in one batch); the verdict is the first failing
 * test case, same as a real judge. {@code passedTestCases}/{@code totalTestCases} record how far it
 * got before that failure; {@code errorMessage} carries a compiler error or
 * runtime stderr detail for a non-{@code ACCEPTED} result, {@code null} otherwise.
 */
@Entity
@Table(name = "SUBMISSION", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "SUBMISSION_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "problem")
@ToString(exclude = {"problem", "sourceCode"})
public class Submission extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_ID", nullable = false)
    private Problem problem;

    @NotNull
    @Size(max = 36)
    @Column(name = "USER_UUID", length = 36, nullable = false)
    private String userUuid;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "LANGUAGE", length = 50, nullable = false)
    private ProgrammingLanguage language;

    @NotNull
    @Column(name = "SOURCE_CODE", nullable = false)
    private String sourceCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50, nullable = false)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Column(name = "PASSED_TEST_CASES")
    private Integer passedTestCases;

    @Column(name = "TOTAL_TEST_CASES")
    private Integer totalTestCases;

    @Column(name = "ERROR_MESSAGE")
    private String errorMessage;

    /**
     * ACCEPTED only: the slowest test case's CPU time in milliseconds, as Judge0 measured it. Includes
     * the language runtime's own start-up and the harness's JSON parsing, so it compares solutions in
     * the same language, not across languages. {@code null} for any other status, or if not measured.
     */
    @Column(name = "RUNTIME_MS")
    private Integer runtimeMs;

    /** ACCEPTED only: the highest peak memory of any test case, in KB (runtime included, as above). */
    @Column(name = "MEMORY_KB")
    private Integer memoryKb;

    /** A user's attempt, or an admin's reference run that can verify the problem for publishing. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "KIND", length = 20, nullable = false)
    @Builder.Default
    private SubmissionKind kind = SubmissionKind.USER;

    /**
     * The problem's {@code contractVersion} this submission was judged against — stamped when judging
     * loads the test cases, so it records exactly which test data the verdict is about. {@code null}
     * until judging starts.
     */
    @Column(name = "CONTRACT_VERSION")
    private Integer contractVersion;

    /**
     * REFERENCE only: publish the problem automatically if this run is judged {@code ACCEPTED} —
     * see {@code ProblemService#publishIfVerified}. Persisted because the verdict arrives
     * asynchronously, long after the request that asked for it has returned.
     */
    @NotNull
    @Column(name = "PUBLISH_ON_ACCEPT", nullable = false)
    @Builder.Default
    private Boolean publishOnAccept = false;
}
