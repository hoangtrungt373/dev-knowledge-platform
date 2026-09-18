package com.ttg.devknowledgeplatform.devpractice.dto;

import java.time.Instant;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

public record SubmissionResponse(
        Integer id,
        Integer problemId,
        String problemTitle,
        ProgrammingLanguage language,
        String sourceCode,
        SubmissionStatus status,
        Instant submittedAt) {
}
