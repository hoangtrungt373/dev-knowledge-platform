package com.ttg.devknowledgeplatform.devpractice.dto;

import java.time.Instant;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

public record SubmissionResponse(
        Integer id,
        Integer problemId,
        String problemTitle,
        ProgrammingLanguage language,
        String sourceCode,
        SubmissionStatus status,
        Integer passedTestCases,
        Integer totalTestCases,
        String errorMessage,
        Instant submittedAt,
        SubmissionKind kind,
        Integer contractVersion,
        // REFERENCE only: the run was asked to publish its draft problem once ACCEPTED.
        Boolean publishOnAccept,
        // ACCEPTED only: slowest test case's CPU time (ms) and highest peak memory (KB); null otherwise.
        Integer runtimeMs,
        Integer memoryKb) {
}
