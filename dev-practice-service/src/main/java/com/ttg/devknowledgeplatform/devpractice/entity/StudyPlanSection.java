package com.ttg.devknowledgeplatform.devpractice.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
 * One chapter of a {@link StudyPlan} — a free-text title and a position, deliberately not a link to
 * {@link ProblemTag}: real plans group by topic (NeetCode 150's "Arrays &amp; Hashing"), by week
 * (Grind 75) or by day ("30 Days of ..."), and only a plain title fits all three.
 */
@Entity
@Table(name = "STUDY_PLAN_SECTION", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "STUDY_PLAN_SECTION_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"studyPlan", "problems"})
@ToString(exclude = {"studyPlan", "problems"})
public class StudyPlanSection extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STUDY_PLAN_ID", nullable = false)
    private StudyPlan studyPlan;

    @NotNull
    @Size(max = 150)
    @Column(name = "TITLE", length = 150, nullable = false)
    private String title;

    @Column(name = "DESCRIPTION")
    private String description;

    /** Order within the plan, 0-based. */
    @NotNull
    @Column(name = "POSITION", nullable = false)
    private Integer position;

    @OrderBy("position ASC")
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StudyPlanProblem> problems = new ArrayList<>();

    /** Appends a problem at the next position and links it back to this section. */
    public StudyPlanProblem addProblem(Problem problem) {
        StudyPlanProblem entry = StudyPlanProblem.builder()
                .section(this).problem(problem).position(problems.size()).build();
        problems.add(entry);
        return entry;
    }

    /**
     * This section's problems a learner may see, in plan order — drafts (not yet verified and
     * published) are left out, so a plan can list a problem before its content is ready.
     */
    public List<Problem> publishedProblems() {
        return problems.stream().map(StudyPlanProblem::getProblem).filter(Problem::isPublished).toList();
    }
}
