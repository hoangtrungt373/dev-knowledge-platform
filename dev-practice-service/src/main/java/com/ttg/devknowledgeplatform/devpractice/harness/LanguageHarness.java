package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Turns a submission's method body into a full, standalone program Judge0 can compile and run —
 * one implementation per {@link ProgrammingLanguage}, selected by {@link LanguageHarnessRegistry}
 * (Strategy: which harness to use is chosen per submission's declared language).
 *
 * <p><b>Template Method:</b> {@link #buildProgram} is the fixed skeleton — prelude, then the
 * user's own code verbatim, then a generated {@code main} that reads a {@link Problem}'s {@code
 * TestCase.input} (a JSON array of argument values) from stdin, calls the user's method with those
 * arguments, and writes the JSON-encoded return value to stdout. Each concrete subclass supplies
 * only the language-specific steps ({@link #renderPrelude}/{@link #renderMain}); the overall
 * parse-stdin → invoke → write-stdout shape never varies.
 *
 * <p>Every implementation is restricted to the closed {@code enums.ParamType} vocabulary — there is
 * no general-purpose JSON/reflection support here, only exactly what that enum defines, since each
 * harness has to hand-render (de)serialization code for its own language's standard library (most
 * runtimes Judge0 executes in have no application classpath, so this module's own Jackson usage is
 * never available inside a submission's sandboxed run).
 */
public abstract class LanguageHarness {

    /** The language this harness generates programs for. */
    public abstract ProgrammingLanguage language();

    /**
     * Builds the full program Judge0 should compile/run for one test case.
     *
     * @param problem  the problem being submitted against (supplies the method signature)
     * @param userCode the submission's own source (a method/class body, never a full program)
     * @return the complete program text
     */
    public final String buildProgram(Problem problem, String userCode) {
        return renderPrelude() + userCode.strip() + "\n\n" + renderMain(problem);
    }

    /**
     * The stub shown to a user before they've written anything — the method signature with an
     * empty body, in this harness's own language syntax.
     *
     * @param problem the problem to render a stub for
     * @return starter source code
     */
    public abstract String renderStarterCode(Problem problem);

    /** Imports/boilerplate that must appear before the user's own code. */
    protected abstract String renderPrelude();

    /** The generated entry point: parse stdin, invoke the user's method, print the JSON result. */
    protected abstract String renderMain(Problem problem);
}
