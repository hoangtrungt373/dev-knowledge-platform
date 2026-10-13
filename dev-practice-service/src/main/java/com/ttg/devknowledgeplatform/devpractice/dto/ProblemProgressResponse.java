package com.ttg.devknowledgeplatform.devpractice.dto;

import java.time.Instant;

import com.ttg.devknowledgeplatform.devpractice.enums.ProblemProgressStatus;

/**
 * One problem the caller has submitted to, whether they've solved it yet, and when they first did
 * ({@code solvedAt}, {@code null} while only attempted).
 */
public record ProblemProgressResponse(Integer problemId, ProblemProgressStatus status, Instant solvedAt) {
}
