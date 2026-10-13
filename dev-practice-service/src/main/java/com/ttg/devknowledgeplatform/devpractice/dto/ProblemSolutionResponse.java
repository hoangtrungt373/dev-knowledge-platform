package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

/**
 * One editorial approach as a learner sees it. {@code codes} holds only judge-accepted implementations
 * at the problem's current contract version, ordered by language — it may be empty while an approach's
 * runs are still being judged or after the test data changed.
 */
public record ProblemSolutionResponse(Integer id, String title, String explanation, List<SolutionCodeResponse> codes) {
}
