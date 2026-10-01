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
 * The join row between a {@link Problem} and a {@link ProblemTag} — an explicit entity rather than a
 * bare {@code @ManyToMany}, so the assignment carries audit columns like every other row (same
 * reasoning as {@code ecommerce-service}'s {@code ProductTagAssignment}).
 *
 * <p>Owned by {@code Problem.tagAssignments} (cascade {@code ALL}, {@code orphanRemoval}) — written
 * only through that collection, never saved/deleted through its own repository.
 */
@Entity
@Table(
        name = "PROBLEM_TAG_ASSIGNMENT",
        schema = "dev_practice",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_PROBLEM_TAG_ASSIGNMENT_PAIR", columnNames = {"PROBLEM_ID", "PROBLEM_TAG_ID"}))
@AttributeOverride(name = "id", column = @Column(name = "PROBLEM_TAG_ASSIGNMENT_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"problem", "problemTag"})
@ToString(exclude = {"problem", "problemTag"})
public class ProblemTagAssignment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_ID", nullable = false)
    private Problem problem;

    // Batched so mapping a page of problems resolves all their tags in one IN (...) query instead
    // of one query per assignment.
    @BatchSize(size = 32)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_TAG_ID", nullable = false)
    private ProblemTag problemTag;
}
