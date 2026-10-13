package com.ttg.devknowledgeplatform.devpractice.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.BatchSize;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

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
 * One editorial approach to a {@link Problem} — "Sorting", "Hash Set" — with a Markdown explanation
 * and its implementations: links to judge-verified {@code REFERENCE} submissions, at most one per
 * language ({@link ProblemSolutionCode}). Linking rather than copying code means a learner only ever
 * reads code the judge accepted.
 *
 * <p>Its own small aggregate, not a collection on {@code Problem}: {@code ProblemService#update}
 * replaces a problem's parameters/test cases/tags wholesale, and solutions must not ride along in
 * that payload. The {@code PROBLEM} FK cascades on delete.
 */
@Entity
@Table(name = "PROBLEM_SOLUTION", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "PROBLEM_SOLUTION_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"problem", "codes"})
@ToString(exclude = {"problem", "codes"})
public class ProblemSolution extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_ID", nullable = false)
    private Problem problem;

    @NotNull
    @Size(max = 150)
    @Column(name = "TITLE", length = 150, nullable = false)
    private String title;

    /** Markdown — rendered by the GUI the same way a problem's description is. */
    @NotNull
    @Column(name = "EXPLANATION", nullable = false)
    private String explanation;

    /** Display order among the problem's solutions, ascending. */
    @NotNull
    @Column(name = "POSITION", nullable = false)
    private Integer position;

    @OrderBy("id ASC")
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "solution", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProblemSolutionCode> codes = new ArrayList<>();

    /** Links a reference run as one of this approach's implementations. */
    public ProblemSolutionCode addCode(Submission referenceRun) {
        ProblemSolutionCode code = ProblemSolutionCode.builder().solution(this).submission(referenceRun).build();
        codes.add(code);
        return code;
    }

    /**
     * The implementations a learner may see, ordered by language: only runs the judge
     * {@code ACCEPTED} at the problem's <em>current</em> contract version. A run still pending, one
     * that failed, or one judged against since-changed test data is left out — its code would no
     * longer be proven correct — so an approach can show its explanation with no code until an admin
     * links a fresh run.
     */
    public List<Submission> verifiedCode() {
        Integer contractVersion = problem.getContractVersion();
        return codes.stream()
                .map(ProblemSolutionCode::getSubmission)
                .filter(s -> s.getKind() == SubmissionKind.REFERENCE
                        && s.getStatus() == SubmissionStatus.ACCEPTED
                        && Objects.equals(s.getContractVersion(), contractVersion))
                .sorted(Comparator.comparing(Submission::getLanguage))
                .toList();
    }
}
