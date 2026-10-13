package com.ttg.devknowledgeplatform.devpractice.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ttg.devknowledgeplatform.devpractice.dto.AdminProblemSolutionResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSolutionOrderRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSolutionRequest;

import jakarta.validation.Valid;

/**
 * Admin editorial management — a problem's solution approaches, any problem status. {@code ROLE_ADMIN}
 * only (the {@code /api/v1/admin/**} rule in {@code SecurityConfig}); already reachable through
 * {@code gateway}'s {@code /api/v1/admin/problems/**} route. Every write goes through
 * {@code ProblemSolutionService}, so the rules are the seeder's: this problem's REFERENCE runs only,
 * one per language, no hand-written code in explanations.
 */
@RequestMapping("/api/v1/admin/problems/{problemId}/solutions")
public interface ProblemSolutionApi {

    /**
     * @return {@code 200} with the problem's approaches in display order — raw and rendered explanation,
     *         every linked run with its verification state
     */
    @GetMapping
    ResponseEntity<List<AdminProblemSolutionResponse>> list(@PathVariable Integer problemId);

    /**
     * Appends an approach after the existing ones.
     *
     * @return {@code 201} with the approach; {@code 400 PROBLEM_SOLUTION_001..005} for a rule violation
     */
    @PostMapping
    ResponseEntity<AdminProblemSolutionResponse> create(
            @PathVariable Integer problemId, @Valid @RequestBody ProblemSolutionRequest request);

    /**
     * Replaces an approach's title, explanation and linked runs.
     *
     * @return {@code 200}; {@code 404 PROBLEM_SOLUTION_006} if it isn't one of this problem's approaches
     */
    @PutMapping("/{solutionId}")
    ResponseEntity<AdminProblemSolutionResponse> update(
            @PathVariable Integer problemId, @PathVariable Integer solutionId,
            @Valid @RequestBody ProblemSolutionRequest request);

    /**
     * Deletes an approach (its linked runs stay — they're the problem's reference history).
     *
     * @return {@code 204}
     */
    @DeleteMapping("/{solutionId}")
    ResponseEntity<Void> delete(@PathVariable Integer problemId, @PathVariable Integer solutionId);

    /**
     * Reorders the approaches. A literal path segment, so Spring matches it ahead of {@code /{solutionId}}.
     *
     * @return {@code 200} with the approaches in their new order; {@code 400 PROBLEM_SOLUTION_007} if the
     *         ids aren't exactly the problem's approaches
     */
    @PutMapping("/order")
    ResponseEntity<List<AdminProblemSolutionResponse>> reorder(
            @PathVariable Integer problemId, @Valid @RequestBody ProblemSolutionOrderRequest request);
}
