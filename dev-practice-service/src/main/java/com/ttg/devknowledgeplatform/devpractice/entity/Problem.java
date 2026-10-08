package com.ttg.devknowledgeplatform.devpractice.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.BatchSize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A coding problem in the practice catalog — a title, a Markdown problem statement, a difficulty
 * tier, and the {@link TestCase} rows a submission is judged against.
 *
 * <p>Reuses {@code common.enums.ContentStatus} (DRAFT/PUBLISHED/ARCHIVED) for its publish
 * lifecycle rather than inventing a new one — the exact same three-state shape
 * {@code content-service}'s {@code Article}/{@code QuestionAnswer} already use, and {@code
 * common} exists precisely to hold a value type like this once more than one module needs it
 * (see {@code common/CLAUDE.md}). {@code difficulty} is deliberately its own local enum instead
 * (see {@link Difficulty}'s Javadoc) — a coincidental three-value shape is not the same concept.
 *
 * <p>{@code authorUuid} is a plain column (the creating admin's Keycloak {@code sub} claim),
 * never a {@code User} foreign key — same "Option C" shape as every other standalone service's
 * owner/author column in this reactor (see root {@code CLAUDE.md}'s Security section).
 *
 * <p>{@code methodName}/{@code returnType}/{@code parameters} define the LeetCode-style method
 * signature a submission implements (a submission is a method body, e.g. a {@code class Solution}
 * with this exact method, never a full stdin/stdout program) — see {@code harness.LanguageHarness}
 * for how these get rendered into a runnable program per language, and {@link TestCase}'s own
 * Javadoc for how {@code input}/{@code expectedOutput} relate to {@code parameters}/
 * {@code returnType}.
 */
@Entity
@Table(name = "PROBLEM", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "PROBLEM_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"testCases", "parameters", "tagAssignments"})
@ToString(exclude = {"testCases", "parameters", "tagAssignments"})
public class Problem extends AbstractEntity {

    @NotNull
    @Size(max = 255)
    @Column(name = "TITLE", length = 255, nullable = false)
    private String title;

    @NotNull
    @Size(max = 255)
    @Column(name = "SLUG", length = 255, nullable = false, unique = true)
    private String slug;

    @NotNull
    @Column(name = "DESCRIPTION", nullable = false)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "DIFFICULTY", length = 50, nullable = false)
    private Difficulty difficulty;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50, nullable = false)
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;

    @NotNull
    @Size(max = 36)
    @Column(name = "AUTHOR_UUID", length = 36, nullable = false)
    private String authorUuid;

    @Column(name = "PUBLISHED_AT")
    private Instant publishedAt;

    @NotNull
    @Size(max = 100)
    @Column(name = "METHOD_NAME", length = 100, nullable = false)
    private String methodName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "RETURN_TYPE", length = 50, nullable = false)
    private ParamType returnType;

    /**
     * Bumped whenever the grading contract changes — the method signature, or any test case's input
     * or expected output (not its {@code sample} flag, which doesn't affect solvability). A reference
     * submission only verifies the problem at the version it was judged against, so an edit to the
     * test data needs a fresh reference before the problem can be (re)published. Not to be confused
     * with {@code version}, Hibernate's optimistic-locking counter, which changes on every save.
     */
    @NotNull
    @Column(name = "CONTRACT_VERSION", nullable = false)
    @Builder.Default
    private Integer contractVersion = 1;

    @OrderBy("position ASC")
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MethodParameter> parameters = new ArrayList<>();

    @OrderBy("id ASC")
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TestCase> testCases = new ArrayList<>();

    /** Topic tags — replace-all on every create/update, same as parameters/test cases. */
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProblemTagAssignment> tagAssignments = new ArrayList<>();

    /** Whether learners can see this problem and submit to it. */
    public boolean isPublished() {
        return ContentStatus.PUBLISHED.equals(status);
    }

    /**
     * Moves the problem to {@code PUBLISHED}. {@code publishedAt} is stamped the first time only, so a
     * problem archived and published again keeps its original publish date. Callers check the publish
     * rule (an accepted reference at the current contract version) first — this only changes state.
     */
    public void publish() {
        status = ContentStatus.PUBLISHED;
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
    }
}
