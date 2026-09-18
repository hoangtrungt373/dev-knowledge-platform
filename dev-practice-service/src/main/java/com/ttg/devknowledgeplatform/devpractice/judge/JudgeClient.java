package com.ttg.devknowledgeplatform.devpractice.judge;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * This module's Adapter over Judge0 CE's submission API — the seam that lets Judge0 be swapped for
 * a different execution backend (e.g. a custom sandboxed-Docker judge, if Judge0 ever proves
 * insufficient) without touching {@code event.SubmissionJudgeEventListener} or any
 * {@code harness.LanguageHarness}. See this module's {@code CLAUDE.md} for why Judge0 was chosen
 * for this phase over rolling a sandbox from scratch.
 */
public interface JudgeClient {

    /**
     * Runs {@code program} against {@code stdin} and blocks (polling internally) until Judge0
     * reports a final status.
     *
     * @param program the full, standalone program to compile/run (see
     *                {@code harness.LanguageHarness#buildProgram})
     * @param language the program's language
     * @param stdin    the input to feed the program (a {@code TestCase.input} JSON array)
     * @return the final result
     */
    Judge0SubmissionResult run(String program, ProgrammingLanguage language, String stdin);
}
