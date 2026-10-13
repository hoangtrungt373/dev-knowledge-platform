package com.ttg.devknowledgeplatform.devpractice.service;

import java.time.Instant;

import com.ttg.devknowledgeplatform.devpractice.enums.ProblemProgressStatus;

/**
 * One problem's standing for one learner — a service-layer result (services never return this
 * module's {@code dto/} classes), mapped to {@code ProblemProgressResponse} by {@code SubmissionMapper}.
 *
 * @param problemId the problem's primary key
 * @param status    solved or only attempted
 * @param solvedAt  when the first accepted submission was made; {@code null} while only attempted
 */
public record ProblemProgress(Integer problemId, ProblemProgressStatus status, Instant solvedAt) {
}
