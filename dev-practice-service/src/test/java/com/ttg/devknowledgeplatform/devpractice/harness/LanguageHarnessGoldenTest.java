package com.ttg.devknowledgeplatform.devpractice.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.ttg.devknowledgeplatform.devpractice.harness.HarnessFixtures.GoldenFixture;

/**
 * Golden-file (snapshot) test for every {@link LanguageHarness}: renders each
 * {@link HarnessFixtures#GOLDEN_FIXTURES fixture}'s starter code and full program per language and
 * compares it byte-for-byte against a checked-in file under
 * {@code src/test/resources/harness/golden/{fixture}/{language}/}.
 *
 * <p>This pins the harnesses' exact output, so any change to what they generate — intended or
 * not — shows up as a readable diff of real source code in review. It does <b>not</b> prove the
 * generated code runs; {@code LanguageHarnessExecutionIT} does that against real runtimes.
 *
 * <p><b>Regenerating after an intended change:</b> run with {@code -Dharness.golden.update=true}
 * (it rewrites every golden file instead of comparing), then review the resulting diff before
 * committing it — a golden update is a deliberate act, never a way to make a red test green.
 */
class LanguageHarnessGoldenTest {

    /** Resolved against the module directory, which is surefire's (and IntelliJ's) default working directory. */
    private static final Path GOLDEN_ROOT = Path.of("src", "test", "resources", "harness", "golden");

    private static final boolean UPDATE = Boolean.getBoolean("harness.golden.update");

    static Stream<Arguments> cases() {
        return HarnessFixtures.GOLDEN_FIXTURES.stream()
                .flatMap(fixture -> HarnessFixtures.ALL_HARNESSES.stream()
                        .map(harness -> Arguments.of(fixture, Named.of(harness.language().name(), harness))));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("cases")
    void starterCodeMatchesGolden(GoldenFixture fixture, LanguageHarness harness) {
        assertMatchesGolden(goldenPath(fixture, harness, "starter"), harness.renderStarterCode(fixture.problem().get()));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("cases")
    void programMatchesGolden(GoldenFixture fixture, LanguageHarness harness) {
        String program = harness.buildProgram(fixture.problem().get(), fixture.solution(harness.language()));
        assertMatchesGolden(goldenPath(fixture, harness, "program"), program);
    }

    private static Path goldenPath(GoldenFixture fixture, LanguageHarness harness, String kind) {
        String language = harness.language().name().toLowerCase();
        return GOLDEN_ROOT.resolve(fixture.id()).resolve(language)
                .resolve(kind + "." + HarnessFixtures.extension(harness.language()) + ".golden");
    }

    private static void assertMatchesGolden(Path golden, String actual) {
        try {
            if (UPDATE) {
                Files.createDirectories(golden.getParent());
                Files.writeString(golden, actual, StandardCharsets.UTF_8);
                return;
            }
            if (!Files.exists(golden)) {
                fail("Missing golden file " + golden.toAbsolutePath()
                        + " — run once with -Dharness.golden.update=true to create it, then review it.");
            }
            // Normalizes CRLF in case a checkout ignores .gitattributes' eol=lf for golden files.
            String expected = Files.readString(golden, StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertThat(actual).as("rendered output vs. golden file %s", golden).isEqualTo(expected);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read/write golden file " + golden, e);
        }
    }
}
