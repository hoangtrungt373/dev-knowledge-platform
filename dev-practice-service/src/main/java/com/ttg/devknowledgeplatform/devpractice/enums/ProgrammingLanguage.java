package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * A submission's source language. Phase 1 only persists this choice alongside the submitted
 * source code; the follow-up judging phase will select a {@code JudgeClient} compile/run
 * Strategy implementation per value (see this module's {@code CLAUDE.md}).
 */
public enum ProgrammingLanguage {
    JAVA,
    PYTHON,
    JAVASCRIPT
}
