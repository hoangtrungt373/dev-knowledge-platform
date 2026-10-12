package com.ttg.devknowledgeplatform.devpractice.dto;

import java.time.Instant;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionRanking;

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
        Integer memoryKb,
        // "Beats X%" — GET by id of an ACCEPTED submission only (computed on read; null in lists).
        // Percentages 0–100; null when rankedAgainst is 0 (nobody else has solved it in this language).
        Double runtimeBeatsPercent,
        Double memoryBeatsPercent,
        Long rankedAgainst) {

    /** A copy carrying the ranking — the mapper can't compute it (it needs a repository query). */
    public SubmissionResponse withRanking(SubmissionRanking ranking) {
        return new SubmissionResponse(id, problemId, problemTitle, language, sourceCode, status, passedTestCases,
                totalTestCases, errorMessage, submittedAt, kind, contractVersion, publishOnAccept, runtimeMs, memoryKb,
                ranking.runtimeBeatsPercent(), ranking.memoryBeatsPercent(), ranking.comparedWith());
    }
}
