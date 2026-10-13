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
 * A curated, ordered study path through the problem catalog — "NeetCode 150", "Blind 75" — made of
 * ordered {@link StudyPlanSection}s, each holding ordered problems. The aggregate root: sections and
 * their entries are written only through {@link #sections} (cascade {@code ALL}, {@code orphanRemoval}).
 *
 * <p>Plans reference problems in any status; the public view shows only published ones (see
 * {@link StudyPlanSection#publishedProblems()}), so a plan can be laid out ahead of its content and
 * fills in as problems get verified and published.
 *
 * <p>No learner progress is stored here: progress is derived per caller from their own submissions,
 * the same way the catalog's solved markers are.
 */
@Entity
@Table(name = "STUDY_PLAN", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "STUDY_PLAN_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "sections")
@ToString(exclude = "sections")
public class StudyPlan extends AbstractEntity {

    @NotNull
    @Size(max = 150)
    @Column(name = "NAME", length = 150, nullable = false)
    private String name;

    @NotNull
    @Size(max = 150)
    @Column(name = "SLUG", length = 150, nullable = false, unique = true)
    private String slug;

    @Column(name = "DESCRIPTION")
    private String description;

    /** Display order on the plans overview (ascending; ties fall back to name). */
    @NotNull
    @Column(name = "POSITION", nullable = false)
    private Integer position;

    @OrderBy("position ASC")
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "studyPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StudyPlanSection> sections = new ArrayList<>();

    /** Appends a section at the next position and links it back to this plan. */
    public StudyPlanSection addSection(String title, String description) {
        StudyPlanSection section = StudyPlanSection.builder()
                .studyPlan(this).title(title).description(description).position(sections.size()).build();
        sections.add(section);
        return section;
    }
}
