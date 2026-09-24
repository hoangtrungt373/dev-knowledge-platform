package com.ttg.devknowledgeplatform.devpractice.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.judge.OutputMatcher;

/**
 * Proves every {@link LanguageHarness}'s generated program actually compiles and runs — what
 * {@link LanguageHarnessGoldenTest} structurally can't. Each case is executed inside the same
 * runtime <em>version</em> Judge0 CE uses for this module's configured language ids
 * ({@code app.judge0.language-ids}: 62 = OpenJDK 13, 71 = Python 3.8, 63 = Node.js 12), since
 * generated code that only works on a newer runtime would fail on the real judge.
 *
 * <p>Cases: each golden fixture's reference solution, plus an {@code identity(T value) -> T}
 * round-trip for every {@link ParamType} in every language — together these exercise every type's
 * declaration, JSON-parse, and JSON-write path. The identity solutions are derived from each
 * harness's own starter code (the empty body filled with {@code return value}), so starter code is
 * proven to compile too.
 *
 * <p><strong>Requires Docker</strong>, and is skipped (not failed) without one. The {@code IT}
 * suffix keeps it out of a plain {@code mvn test}; run it explicitly with
 * {@code -Dtest=LanguageHarnessExecutionIT}.
 */
@Testcontainers(disabledWithoutDocker = true)
class LanguageHarnessExecutionIT {

    @Container
    private static final GenericContainer<?> JAVA_RUNTIME = idleContainer("openjdk:13-jdk-slim");

    @Container
    private static final GenericContainer<?> PYTHON_RUNTIME = idleContainer("python:3.8-slim");

    @Container
    private static final GenericContainer<?> NODE_RUNTIME = idleContainer("node:12-slim");

    private static final OutputMatcher OUTPUT_MATCHER = new OutputMatcher(new ObjectMapper());

    /**
     * One program to run and the stdout it must produce.
     *
     * @param name           display name for the parameterized test
     * @param harness        the harness generating the program
     * @param problem        the signature to generate against
     * @param userCode       the "submission" wrapped by the harness
     * @param stdin          the {@code TestCase.input}-shaped JSON argument array
     * @param expectedOutput the JSON value the program must print
     */
    record ExecutionCase(String name, LanguageHarness harness, Problem problem, String userCode,
                         String stdin, String expectedOutput) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<ExecutionCase> cases() {
        Stream<ExecutionCase> fixtures = HarnessFixtures.GOLDEN_FIXTURES.stream()
                .flatMap(fixture -> HarnessFixtures.ALL_HARNESSES.stream()
                        .map(harness -> new ExecutionCase(
                                fixture.id() + " / " + harness.language(), harness, fixture.problem().get(),
                                fixture.solution(harness.language()), fixture.stdin(), fixture.expectedOutput())));

        Stream<ExecutionCase> identities = Arrays.stream(ParamType.values())
                .flatMap(type -> HarnessFixtures.ALL_HARNESSES.stream().map(harness -> {
                    Problem problem = HarnessFixtures.identity(type);
                    String value = sampleValue(type, harness.language());
                    return new ExecutionCase("identity " + type + " / " + harness.language(), harness, problem,
                            identitySolution(harness, problem), "[" + value + "]", value);
                }));

        return Stream.concat(fixtures, identities);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void generatedProgramRunsAndPrintsExpectedOutput(ExecutionCase testCase) throws Exception {
        String program = testCase.harness().buildProgram(testCase.problem(), testCase.userCode());

        ExecResult result = run(testCase.harness().language(), program, testCase.stdin());

        assertThat(result.getExitCode())
                .as("exit code; stderr:%n%s%nprogram:%n%s", result.getStderr(), program)
                .isZero();
        // The exact comparison production judging uses, so this also proves cross-language output
        // (e.g. JavaScript printing 2 for a double 2.0) is judged correctly, not just printed.
        assertThat(OUTPUT_MATCHER.matches(result.getStdout(), testCase.expectedOutput(), testCase.problem().getReturnType()))
                .as("stdout %s should match expected %s", result.getStdout(), testCase.expectedOutput())
                .isTrue();
    }

    /**
     * A representative value per type — deliberately including escapes, negatives, empty nesting,
     * whole-number doubles (rendered as {@code 2} by JavaScript), and a {@code long} beyond 2<sup>53</sup>.
     */
    private static String sampleValue(ParamType type, ProgrammingLanguage language) {
        return switch (type) {
            case INT -> "-42";
            // JavaScript numbers are IEEE-754 doubles — a long beyond 2^53 can't survive JSON.parse
            // there at all (same limitation LeetCode's own JS judge has), so it gets a smaller one.
            case LONG -> language == ProgrammingLanguage.JAVASCRIPT ? "1234567890123" : "9007199254740993";
            case DOUBLE -> "2.0";
            case BOOLEAN -> "false";
            // Quotes, backslash, newline/tab/control-char escapes, a \\u escape, and a raw
            // non-ASCII char (UTF-8 bytes on stdin, whatever the sandbox JVM's default charset).
            case STRING -> "\"a \\\"quoted\\\" \\\\ path\\nnext\\tline é \\u2603 \\u0001\"";
            case INT_ARRAY -> "[3,-1,0]";
            case DOUBLE_ARRAY -> "[1.5,-0.25,3.0]";
            case BOOLEAN_ARRAY -> "[true,false]";
            case STRING_ARRAY -> "[\"x\",\"y z\"]";
            case INT_MATRIX -> "[[1,2],[],[3]]";
        };
    }

    /** Fills the harness's own empty starter-code body with {@code return value}. */
    private static String identitySolution(LanguageHarness harness, Problem problem) {
        String starter = harness.renderStarterCode(problem);
        String solution = switch (harness.language()) {
            case JAVA -> starter.replace("{\n\n    }", "{\n        return value;\n    }");
            case PYTHON -> starter.replace("pass", "return value");
            case JAVASCRIPT -> starter.replace("{\n\n};", "{\n    return value;\n};");
        };
        assertThat(solution).as("starter code shape changed; update identitySolution").isNotEqualTo(starter);
        return solution;
    }

    private static ExecResult run(ProgrammingLanguage language, String program, String stdin) throws Exception {
        record Runtime(GenericContainer<?> container, String fileName, String command) {
        }
        Runtime runtime = switch (language) {
            case JAVA -> new Runtime(JAVA_RUNTIME, "Main.java", "javac Main.java && java Main < input.json");
            case PYTHON -> new Runtime(PYTHON_RUNTIME, "program.py", "python3 program.py < input.json");
            case JAVASCRIPT -> new Runtime(NODE_RUNTIME, "program.js", "node program.js < input.json");
        };

        // A fresh directory per case — the containers are shared across the whole test class.
        String dir = "/work/" + UUID.randomUUID();
        runtime.container().copyFileToContainer(Transferable.of(program), dir + "/" + runtime.fileName());
        runtime.container().copyFileToContainer(Transferable.of(stdin), dir + "/input.json");
        return runtime.container().execInContainer("sh", "-c", "cd " + dir + " && " + runtime.command());
    }

    /** A container that just stays alive, so each case can {@code exec} into it without a restart. */
    private static GenericContainer<?> idleContainer(String image) {
        return new GenericContainer<>(DockerImageName.parse(image)).withCommand("sleep", "infinity");
    }
}
