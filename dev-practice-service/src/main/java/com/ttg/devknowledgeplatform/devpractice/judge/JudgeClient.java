package com.ttg.devknowledgeplatform.devpractice.judge;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Runs programs on a code-execution backend and returns what each one did — an <b>Adapter</b> in
 * front of Judge0's HTTP API (see {@code judge.impl.Judge0Client}), so nothing outside this package
 * depends on Judge0's wire format.
 *
 * <p>Batch-only on purpose: every caller has several inputs to run (a submission's test cases, a
 * Run's cases), and one batch costs a fixed number of HTTP round trips however many programs it
 * holds, where running them one by one costs a full submit-and-poll cycle each. A single program is
 * just a batch of one.
 */
public interface JudgeClient {

    /**
     * Runs every request and waits until all of them have finished.
     *
     * @param requests what to run — may mix languages and programs; may be empty
     * @return one result per request, in the same order
     * @throws JudgeUnavailableException if the backend can't be reached or rejects a request
     */
    List<Judge0SubmissionResult> runAll(List<JudgeRequest> requests);

    /**
     * One program run on one input.
     *
     * @param program  the complete program (harness + user code)
     * @param language the language it's written in
     * @param stdin    what the program reads — a JSON argument array
     */
    record JudgeRequest(String program, ProgrammingLanguage language, String stdin) {
    }
}
