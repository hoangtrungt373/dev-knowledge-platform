package com.ttg.devknowledgeplatform.devpractice.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * Full problem detail — used by both the admin ({@code getById}, all test cases) and public
 * ({@code getBySlug}, sample test cases only) endpoints. See
 * {@link com.ttg.devknowledgeplatform.devpractice.mapper.ProblemMapper#toPublicResponse} for the
 * filtering that keeps hidden test cases out of the public response.
 *
 * <p>{@code verified} is admin-only: {@code true} once an {@code ACCEPTED} reference submission
 * exists at the current {@code contractVersion} (the condition for publishing). It's {@code null} —
 * and omitted from the JSON — on public responses, which have no use for it.
 */
public record ProblemResponse(
        Integer id,
        String slug,
        String title,
        String description,
        Difficulty difficulty,
        ContentStatus status,
        String methodName,
        ParamType returnType,
        List<MethodParameterResponse> parameters,
        List<TestCaseResponse> testCases,
        List<ProblemTagSummaryResponse> tags,
        Integer contractVersion,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean verified,
        Instant publishedAt,
        Instant createdAt) {

    /** A copy with {@code verified} set — the mapper can't compute it (it needs a repository query). */
    public ProblemResponse withVerified(boolean value) {
        return new ProblemResponse(id, slug, title, description, difficulty, status, methodName, returnType,
                parameters, testCases, tags, contractVersion, value, publishedAt, createdAt);
    }

    /** A copy with only the given test cases — used to strip hidden ones from public responses. */
    public ProblemResponse withTestCases(List<TestCaseResponse> value) {
        return new ProblemResponse(id, slug, title, description, difficulty, status, methodName, returnType,
                parameters, value, tags, contractVersion, verified, publishedAt, createdAt);
    }
}
