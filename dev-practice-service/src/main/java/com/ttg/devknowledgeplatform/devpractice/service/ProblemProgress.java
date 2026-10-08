package com.ttg.devknowledgeplatform.devpractice.service;

import com.ttg.devknowledgeplatform.devpractice.enums.ProblemProgressStatus;

/**
 * One problem's standing for one learner — a service-layer result (services never return this
 * module's {@code dto/} classes), mapped to {@code ProblemProgressResponse} by {@code SubmissionMapper}.
 *
 * @param problemId the problem's primary key
 * @param status    solved or only attempted
 */
public record ProblemProgress(Integer problemId, ProblemProgressStatus status) {
}
