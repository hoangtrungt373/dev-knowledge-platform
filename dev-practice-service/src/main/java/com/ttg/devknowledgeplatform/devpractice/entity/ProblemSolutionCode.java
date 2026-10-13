package com.ttg.devknowledgeplatform.devpractice.entity;

import org.hibernate.annotations.BatchSize;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One implementation of a {@link ProblemSolution}: a link to the {@code REFERENCE} submission whose
 * code it shows. An explicit join entity (audit columns, like {@link ProblemTagAssignment}); owned by
 * {@code ProblemSolution.codes}. "At most one per language per approach" is checked by
 * {@code ProblemSolutionService}, since the language is a column of the submission, not of this row.
 */
@Entity
@Table(
        name = "PROBLEM_SOLUTION_CODE",
        schema = "dev_practice",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_PROBLEM_SOLUTION_CODE_PAIR", columnNames = {"PROBLEM_SOLUTION_ID", "SUBMISSION_ID"}))
@AttributeOverride(name = "id", column = @Column(name = "PROBLEM_SOLUTION_CODE_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"solution", "submission"})
@ToString(exclude = {"solution", "submission"})
public class ProblemSolutionCode extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_SOLUTION_ID", nullable = false)
    private ProblemSolution solution;

    // Batched so a problem's whole editorial resolves its linked runs in one IN (...) query.
    @BatchSize(size = 32)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SUBMISSION_ID", nullable = false)
    private Submission submission;
}
