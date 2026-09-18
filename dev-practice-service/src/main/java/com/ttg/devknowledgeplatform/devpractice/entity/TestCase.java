package com.ttg.devknowledgeplatform.devpractice.entity;

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
 * One input/expected-output pair a {@link Problem}'s submissions are judged against.
 *
 * <p>{@code sample} distinguishes the small set of test cases shown to the user alongside the
 * problem statement (worked examples) from the full, larger hidden set actually used for
 * grading — {@link com.ttg.devknowledgeplatform.devpractice.mapper.ProblemMapper#toPublicResponse}
 * filters to {@code sample = true} rows only, so a public caller can never read a hidden test
 * case's expected output.
 */
@Entity
@Table(name = "TEST_CASE", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "TEST_CASE_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "problem")
@ToString(exclude = "problem")
public class TestCase extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_ID", nullable = false)
    private Problem problem;

    @NotNull
    @Column(name = "INPUT", nullable = false)
    private String input;

    @NotNull
    @Column(name = "EXPECTED_OUTPUT", nullable = false)
    private String expectedOutput;

    @NotNull
    @Column(name = "SAMPLE", nullable = false)
    @Builder.Default
    private Boolean sample = Boolean.FALSE;
}
