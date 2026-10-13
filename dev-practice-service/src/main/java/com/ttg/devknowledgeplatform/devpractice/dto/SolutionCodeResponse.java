package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * One language's implementation of an approach — the accepted reference run's own code and the
 * judge's measurements of it (slowest test case's runtime, peak memory).
 */
public record SolutionCodeResponse(ProgrammingLanguage language, String sourceCode, Integer runtimeMs, Integer memoryKb) {
}
