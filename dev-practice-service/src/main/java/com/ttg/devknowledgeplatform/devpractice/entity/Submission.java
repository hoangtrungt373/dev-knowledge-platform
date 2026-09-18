package com.ttg.devknowledgeplatform.devpractice.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
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
 * through Judge0 (via {@code judge.JudgeClient}) or the first failing test case is hit, whichever
 * comes first (grading stops at the first failure, same as a real judge). {@code passedTestCases}/
 * {@code totalTestCases} record how far it got; {@code errorMessage} carries a compiler error or
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
}
