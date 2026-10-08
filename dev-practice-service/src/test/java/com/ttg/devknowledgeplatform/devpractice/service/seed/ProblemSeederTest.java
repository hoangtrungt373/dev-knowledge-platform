package com.ttg.devknowledgeplatform.devpractice.service.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaScriptSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.PythonSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.SignatureTemplateParserRegistry;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

/**
 * Loads the real {@code data/problems/*.md} seed files through {@link ProblemSeeder} (real template
 * parsers; repositories and {@link ProblemService} mocked) and checks what would be created —
 * including compiling the seed file's own {@code referenceSolution} and running every Evaluate Reverse
 * Polish Notation test case through it, so a wrong {@code expectedOutput} (or a broken reference) fails
 * the build here rather than leaving the problem stuck as a draft when the real judge rejects it.
 */
class ProblemSeederTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ProblemTagRepository tagRepository = mock(ProblemTagRepository.class);
    private final ProblemService problemService = mock(ProblemService.class);
    private final SubmissionService submissionService = mock(SubmissionService.class);
    private final SlugService slugService = mock(SlugService.class);
    private final ProblemSeeder seeder = new ProblemSeeder(problemRepository, tagRepository, problemService, submissionService,
            new SignatureTemplateParserRegistry(List.of(new JavaSignatureTemplateParser(),
                    new PythonSignatureTemplateParser(), new JavaScriptSignatureTemplateParser())),
            slugService);
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(problemService.create(any(), anyString())).thenAnswer(inv -> {
            Problem saved = Problem.builder().build();
            saved.setId(41);
            return saved;
        });
        when(slugService.toSlug(anyString())).thenAnswer(inv -> inv.<String>getArgument(0).toLowerCase().replace(' ', '-'));
        // Only names the tag seeder's CSV actually provides resolve — so a problem file referencing a
        // tag that isn't seeded fails this test, not a real startup.
        List<String> seededTags = ProblemTagSeederTest.csvNames();
        when(tagRepository.findByNameIgnoreCase(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return seededTags.stream().filter(name::equalsIgnoreCase).findFirst().map(n -> {
                ProblemTag tag = ProblemTag.builder().name(n).slug(n.toLowerCase()).build();
                tag.setId(n.hashCode());
                return tag;
            });
        });
    }

    @Test
    void seedsEvaluateReversePolishNotationWithTheSignatureFromItsTemplate() {
        assertThat(seeder.seed()).isEqualTo(1);

        ProblemCommands.Create command = captureCreated();
        assertThat(command.title()).isEqualTo("Evaluate Reverse Polish Notation");
        assertThat(command.difficulty()).isEqualTo(Difficulty.MEDIUM);
        // The file asks for PUBLISHED; that's reached through the reference run, never set directly.
        assertThat(command.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(command.methodName()).isEqualTo("evalRPN");
        assertThat(command.returnType()).isEqualTo(ParamType.INT);
        assertThat(command.parameters())
                .containsExactly(new ProblemCommands.MethodParameterInput("tokens", ParamType.STRING_ARRAY, 0));
        assertThat(command.tagIds()).containsExactlyInAnyOrder("Array".hashCode(), "Math".hashCode(), "Stack".hashCode());
        assertThat(command.description()).startsWith("You are given an array of strings `tokens`")
                .doesNotContain("testCases:");
        assertThat(command.testCases()).hasSize(18);
        assertThat(command.testCases().stream().filter(ProblemCommands.TestCaseInput::sample)).hasSize(2);
    }

    @Test
    void submitsTheReferenceSolutionAsAPublishOnAcceptRun() {
        seeder.seed();

        SubmissionCommands.Create reference = captureReference();
        assertThat(reference.problemId()).isEqualTo(41);
        assertThat(reference.language()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(reference.sourceCode()).contains("public int evalRPN(String[] tokens)");
    }

    @Test
    void theSeedFilesReferenceSolutionPassesEveryTestCase(@TempDir Path dir) throws Exception {
        seeder.seed();
        Method evalRPN = compileSolution(captureReference().sourceCode(), dir).getMethod("evalRPN", String[].class);
        // LeetCode-style `class Solution` is package-private: reflective access needs setAccessible.
        evalRPN.setAccessible(true);
        var constructor = evalRPN.getDeclaringClass().getDeclaredConstructor();
        constructor.setAccessible(true);
        Object solution = constructor.newInstance();

        for (ProblemCommands.TestCaseInput testCase : captureCreated().testCases()) {
            JsonNode args = json.readTree(testCase.input());
            assertThat(args.isArray() && args.size() == 1).as("one argument: %s", testCase.input()).isTrue();
            String[] tokens = json.treeToValue(args.get(0), String[].class);
            assertThat(evalRPN.invoke(solution, (Object) tokens)).as("evalRPN(%s)", testCase.input())
                    .isEqualTo(Integer.parseInt(testCase.expectedOutput()));
        }
    }

    @Test
    void skipsAProblemThatAlreadyExists() {
        when(problemRepository.existsBySlug("evaluate-reverse-polish-notation")).thenReturn(true);

        assertThat(seeder.seed()).isZero();
        verify(problemService, never()).create(any(), anyString());
        verify(submissionService, never()).createReference(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    private ProblemCommands.Create captureCreated() {
        ArgumentCaptor<ProblemCommands.Create> captor = ArgumentCaptor.forClass(ProblemCommands.Create.class);
        verify(problemService).create(captor.capture(), eq(ProblemSeeder.SEED_AUTHOR_UUID));
        return captor.getValue();
    }

    private SubmissionCommands.Create captureReference() {
        ArgumentCaptor<SubmissionCommands.Create> captor = ArgumentCaptor.forClass(SubmissionCommands.Create.class);
        verify(submissionService).createReference(eq(ProblemSeeder.SEED_AUTHOR_UUID), captor.capture(), eq(true));
        return captor.getValue();
    }

    /**
     * Compiles a seed solution the way the Java harness sees it — with the harness prelude's
     * {@code import java.util.*;} in front — using the JDK's own compiler ({@code javax.tools}), and
     * loads the resulting {@code Solution} class. Runs on the test JDK, not Judge0's OpenJDK 13; that
     * one is exercised only by {@code LanguageHarnessExecutionIT}.
     */
    private static Class<?> compileSolution(String source, Path dir) throws Exception {
        Path file = dir.resolve("Solution.java");
        Files.writeString(file, "import java.util.*;\n" + source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler.run(null, null, null, "-d", dir.toString(), file.toString()))
                .as("the reference solution compiles").isZero();
        URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, ProblemSeederTest.class.getClassLoader());
        return loader.loadClass("Solution");
    }
}
