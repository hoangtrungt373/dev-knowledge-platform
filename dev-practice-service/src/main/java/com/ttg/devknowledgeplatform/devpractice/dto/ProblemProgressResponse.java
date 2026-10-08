package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.ProblemProgressStatus;

/** One problem the caller has submitted to, and whether they've solved it yet. */
public record ProblemProgressResponse(Integer problemId, ProblemProgressStatus status) {
}
