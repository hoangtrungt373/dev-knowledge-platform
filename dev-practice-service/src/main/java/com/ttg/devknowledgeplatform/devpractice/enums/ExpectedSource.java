package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * Where a Run case's expected answer came from — so the learner can tell a sample's official answer
 * from one computed on the spot, and knows why a case has no answer at all.
 */
public enum ExpectedSource {

    /** The input is one of the problem's sample cases (or the same JSON); its stored answer was used. */
    SAMPLE,

    /** A custom input: the problem's accepted reference solution was run on it, and its output is the answer. */
    REFERENCE,

    /**
     * The reference solution failed on this input too (error or time limit) — usually a sign the input
     * breaks the problem's constraints, so there is no answer to check against.
     */
    REFERENCE_FAILED,

    /**
     * No answer was computed: the problem has no accepted reference at its current version (one
     * published before references were required), or the learner's code didn't compile.
     */
    UNAVAILABLE
}
