package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared {@link Problem} fixtures for the harness tests — built in memory (no database), since a
 * {@link LanguageHarness} only ever reads a problem's method signature.
 */
final class HarnessFixtures {

    /**
     * Every harness implementation, constructed directly rather than through Spring — the
     * harnesses are stateless and have no collaborators, so no application context is needed.
     */
    static final List<LanguageHarness> ALL_HARNESSES = List.of(
            new JavaLanguageHarness(), new PythonLanguageHarness(), new JavaScriptLanguageHarness());

    /**
     * A problem whose rendered starter code and program are pinned by golden files, plus a
     * reference solution per language (read from {@code harness/fixtures/{id}/solution.*}) and one
     * stdin/expected-stdout pair the execution test runs it against.
     *
     * @param id             directory name under {@code harness/fixtures/} and {@code harness/golden/}
     * @param problem        builds the fixture problem
     * @param stdin          a {@code TestCase.input}-shaped JSON argument array
     * @param expectedOutput the JSON value a correct solution prints for {@code stdin}
     */
    record GoldenFixture(String id, Supplier<Problem> problem, String stdin, String expectedOutput) {

        @Override
        public String toString() {
            return id;
        }

        /** Reads this fixture's reference solution for {@code language} from the test classpath. */
        String solution(ProgrammingLanguage language) {
            return readClasspath("harness/fixtures/" + id + "/solution." + extension(language));
        }
    }

    /** Every golden fixture — the classic two-argument case, plus one exercising every {@link ParamType}. */
    static final List<GoldenFixture> GOLDEN_FIXTURES = List.of(
            new GoldenFixture("two-sum", HarnessFixtures::twoSum, "[[2,7,11,15], 9]", "[0,1]"),
            new GoldenFixture("every-param-type", HarnessFixtures::everyParamType,
                    """
                    [1, 1234567890123, 2.5, true, "hello", [1, 2], [1.5], [true, false], ["a", "b"], [[1, 2], [3]]]""",
                    "\"hello\""));

    private HarnessFixtures() {
    }

    /** {@code twoSum(int[] nums, int target) -> int[]}. */
    static Problem twoSum() {
        return problem("twoSum", ParamType.INT_ARRAY, "nums", ParamType.INT_ARRAY, "target", ParamType.INT);
    }

    /** One parameter of every {@link ParamType}, in declaration order, returning the {@code STRING} one. */
    static Problem everyParamType() {
        return problem("describe", ParamType.STRING,
                "a", ParamType.INT, "b", ParamType.LONG, "c", ParamType.DOUBLE, "d", ParamType.BOOLEAN,
                "e", ParamType.STRING, "f", ParamType.INT_ARRAY, "g", ParamType.DOUBLE_ARRAY,
                "h", ParamType.BOOLEAN_ARRAY, "i", ParamType.STRING_ARRAY, "j", ParamType.INT_MATRIX);
    }

    /** {@code identity(T value) -> T} — round-trips one value through a harness's parse and write paths. */
    static Problem identity(ParamType type) {
        return problem("identity", type, "value", type);
    }

    /** The source-file extension each language's fixture and golden files use. */
    static String extension(ProgrammingLanguage language) {
        return switch (language) {
            case JAVA -> "java";
            case PYTHON -> "py";
            case JAVASCRIPT -> "js";
        };
    }

    static String readClasspath(String path) {
        try (InputStream in = HarnessFixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            // Normalizes CRLF in case a checkout ignores .gitattributes' eol=lf for these files.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Builds a problem from alternating {@code name, type} pairs; positions follow argument order. */
    private static Problem problem(String methodName, ParamType returnType, Object... namesAndTypes) {
        Problem problem = Problem.builder().methodName(methodName).returnType(returnType).build();
        for (int k = 0; k < namesAndTypes.length; k += 2) {
            problem.getParameters().add(MethodParameter.builder()
                    .problem(problem)
                    .name((String) namesAndTypes[k])
                    .type((ParamType) namesAndTypes[k + 1])
                    .position(k / 2)
                    .build());
        }
        return problem;
    }
}
