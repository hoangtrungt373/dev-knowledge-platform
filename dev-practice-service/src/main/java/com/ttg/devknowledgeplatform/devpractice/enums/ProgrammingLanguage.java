package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * A submission's source language. Each value carries the Judge0 CE {@code language_id} used to
 * submit code in that language (see {@link #getJudge0LanguageId()}), selected by
 * {@code harness.LanguageHarnessRegistry} to pick the matching {@code harness.LanguageHarness}
 * (Strategy) that wraps a submission's method body into a full runnable program.
 *
 * <p><b>Caveat:</b> Judge0 language ids are per-deployment (they come from that instance's own
 * {@code GET /languages} list and can shift between Judge0 versions) — these are the widely-used
 * Judge0 CE defaults, but verify against the actual self-hosted instance's language list before
 * relying on them; same "unverified-at-runtime" caveat this module's Liquibase changelog already
 * carries.
 */
public enum ProgrammingLanguage {
    JAVA(62),
    PYTHON(71),
    JAVASCRIPT(63);

    private final int judge0LanguageId;

    ProgrammingLanguage(int judge0LanguageId) {
        this.judge0LanguageId = judge0LanguageId;
    }

    public int getJudge0LanguageId() {
        return judge0LanguageId;
    }
}
