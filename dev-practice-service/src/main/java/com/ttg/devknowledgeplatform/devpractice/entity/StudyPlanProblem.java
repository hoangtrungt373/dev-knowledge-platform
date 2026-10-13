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
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One problem's place in a {@link StudyPlanSection} — an explicit join entity rather than a bare
 * {@code @ManyToMany}, because the link carries data of its own ({@link #position}) plus the audit
 * columns every row has (same reasoning as {@link ProblemTagAssignment}). The same problem may sit in
 * many plans, each with its own order.
 *
 * <p>Owned by {@code StudyPlanSection.problems} — written only through that collection.
 */
@Entity
@Table(name = "STUDY_PLAN_PROBLEM", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "STUDY_PLAN_PROBLEM_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"section", "problem"})
@ToString(exclude = {"section", "problem"})
public class StudyPlanProblem extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STUDY_PLAN_SECTION_ID", nullable = false)
    private StudyPlanSection section;

    // Batched so rendering a whole plan resolves all its problems in one IN (...) query instead of
    // one query per entry.
    @BatchSize(size = 32)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_ID", nullable = false)
    private Problem problem;

    /** Order within the section, 0-based. */
    @NotNull
    @Column(name = "POSITION", nullable = false)
    private Integer position;
}
