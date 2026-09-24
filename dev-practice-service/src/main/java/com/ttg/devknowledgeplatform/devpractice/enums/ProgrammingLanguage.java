package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * A submission's source language. Deliberately carries no vendor-specific detail (no Judge0
 * {@code language_id} or equivalent) — this enum is used well beyond the judge subsystem
 * ({@code Submission.language}, {@code harness.LanguageHarnessRegistry}, REST DTOs), so it stays a
 * plain domain vocabulary; translating a value here into whatever a specific execution backend
 * needs is that backend's own adapter's job (see {@code config.JudgeClientProperties#getLanguageIds()}
 * for Judge0's). Extending this enum means adding a matching {@code harness.LanguageHarness} bean
 * (plus its {@code TypeRenderer} and a {@code harness/{language}/} template directory) and a config
 * entry for whichever judge backend is active — never a field on this type itself.
 */
public enum ProgrammingLanguage {
    JAVA,
    PYTHON,
    JAVASCRIPT
}
