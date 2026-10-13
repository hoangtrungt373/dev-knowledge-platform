package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** A problem's approach ids in their new display order — every one of them, exactly once. */
public record ProblemSolutionOrderRequest(@NotNull List<@NotNull Integer> solutionIds) {
}
