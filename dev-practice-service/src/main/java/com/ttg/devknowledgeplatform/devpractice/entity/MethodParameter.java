package com.ttg.devknowledgeplatform.devpractice.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One named, typed parameter of a {@link Problem}'s method signature (LeetCode-style: a submission
 * is a method body, not a full stdin/stdout program — see {@code harness.LanguageHarness}). Ordered
 * by {@code position} (not insertion id) since a signature's parameter order is semantically
 * load-bearing — {@code twoSum(int[] numbers, int target)} is a different signature from
 * {@code twoSum(int target, int[] numbers)}.
 */
@Entity
@Table(name = "METHOD_PARAMETER", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "METHOD_PARAMETER_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "problem")
@ToString(exclude = "problem")
public class MethodParameter extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_ID", nullable = false)
    private Problem problem;

    @NotNull
    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 50, nullable = false)
    private ParamType type;

    @NotNull
    @Column(name = "POSITION", nullable = false)
    private Integer position;
}
