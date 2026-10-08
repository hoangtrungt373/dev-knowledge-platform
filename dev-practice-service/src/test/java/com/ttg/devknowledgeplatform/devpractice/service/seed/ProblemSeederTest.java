package com.ttg.devknowledgeplatform.devpractice.service.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * parsers; repositories and services mocked) and checks what would be created. The central check is
 * generic over every seed file: each file's own {@code referenceSolution} is compiled and run against
 * each of its test cases, so a wrong {@code expectedOutput} (or a broken reference) fails the build here
 * rather than leaving the problem stuck as a draft when the real judge rejects it. A new seed file is
 * covered automatically — no per-problem test needed unless it adds a {@link ParamType} the argument
 * conversion below doesn't handle yet (the exhaustive switch then fails to compile).
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

    /** Ids handed out by the mocked ProblemService#create, in creation order. */
    private final AtomicInteger nextId = new AtomicInteger(100);

    @BeforeEach
    void setUp() {
        when(problemService.create(any(), anyString())).thenAnswer(inv -> {
            Problem saved = Problem.builder().build();
            saved.setId(nextId.getAndIncrement());
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
        seeder.seed();

        ProblemCommands.Create command = createdByTitle().get("Evaluate Reverse Polish Notation");
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
    void seedsContainsDuplicateWithTheSignatureFromItsTemplate() {
        seeder.seed();

        ProblemCommands.Create command = createdByTitle().get("Contains Duplicate");
        assertThat(command.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(command.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(command.methodName()).isEqualTo("containsDuplicate");
        assertThat(command.returnType()).isEqualTo(ParamType.BOOLEAN);
        assertThat(command.parameters())
                .containsExactly(new ProblemCommands.MethodParameterInput("nums", ParamType.INT_ARRAY, 0));
        assertThat(command.tagIds())
                .containsExactlyInAnyOrder("Array".hashCode(), "Hash Table".hashCode(), "Sorting".hashCode());
        assertThat(command.testCases()).hasSize(16);
        assertThat(command.testCases().stream().filter(ProblemCommands.TestCaseInput::sample)).hasSize(2);
        // Both verdicts are exercised, so a solution that always answers one way can't be accepted.
        assertThat(command.testCases()).extracting(ProblemCommands.TestCaseInput::expectedOutput)
                .contains("true", "false");
    }

    @Test
    void submitsEveryProblemsReferenceAsAPublishOnAcceptRunForThatProblem() {
        seeder.seed();

        Map<String, ProblemCommands.Create> created = createdByTitle();
        Map<Integer, SubmissionCommands.Create> references = referencesByProblemId();
        assertThat(references).hasSameSizeAs(created);
        assertThat(references.values()).allMatch(r -> r.language() == ProgrammingLanguage.JAVA);
        // Each reference goes to the id its own problem was created with — not to some other problem.
        int id = 100;
        for (ProblemCommands.Create command : createdInOrder()) {
            assertThat(references.get(id++).sourceCode()).contains(" " + command.methodName() + "(");
        }
    }

    @Test
    void everySeedFilesReferenceSolutionPassesEveryOneOfItsTestCases(@TempDir Path dir) throws Exception {
        seeder.seed();
        List<ProblemCommands.Create> created = createdInOrder();
        Map<Integer, SubmissionCommands.Create> references = referencesByProblemId();
        assertThat(created).isNotEmpty();

        for (int i = 0; i < created.size(); i++) {
            ProblemCommands.Create problem = created.get(i);
            SubmissionCommands.Create reference = references.get(100 + i);
            assertThat(reference).as("'%s' has a reference solution", problem.title()).isNotNull();

            // Every seed solution is a class named Solution — one directory (and class loader) each.
            Method method = compileMethod(reference.sourceCode(), problem, Files.createDirectory(dir.resolve("p" + i)));
            Constructor<?> constructor = method.getDeclaringClass().getDeclaredConstructor();
            constructor.setAccessible(true);

            for (ProblemCommands.TestCaseInput testCase : problem.testCases()) {
                Object[] args = toArguments(json.readTree(testCase.input()), problem.parameters());
                // A fresh instance per case, like the harness — no state leaks between cases.
                Object actual = method.invoke(constructor.newInstance(), args);
                // Typed local: valueToTree is generic in its return type, which assertThat can't infer.
                JsonNode actualJson = json.valueToTree(actual);
                assertThat(actualJson)
                        .as("%s: %s(%s)", problem.title(), problem.methodName(), testCase.input())
                        .isEqualTo(json.readTree(testCase.expectedOutput()));
            }
        }
    }

    @Test
    void skipsProblemsThatAlreadyExist() {
        when(problemRepository.existsBySlug(anyString())).thenReturn(true);

        assertThat(seeder.seed()).isZero();
        verify(problemService, never()).create(any(), anyString());
        verify(submissionService, never()).createReference(any(), any(), anyBoolean());
    }

    private List<ProblemCommands.Create> createdInOrder() {
        ArgumentCaptor<ProblemCommands.Create> captor = ArgumentCaptor.forClass(ProblemCommands.Create.class);
        verify(problemService, atLeastOnce()).create(captor.capture(), eq(ProblemSeeder.SEED_AUTHOR_UUID));
        return captor.getAllValues();
    }

    private Map<String, ProblemCommands.Create> createdByTitle() {
        return createdInOrder().stream().collect(Collectors.toMap(ProblemCommands.Create::title, Function.identity()));
    }

    private Map<Integer, SubmissionCommands.Create> referencesByProblemId() {
        ArgumentCaptor<SubmissionCommands.Create> captor = ArgumentCaptor.forClass(SubmissionCommands.Create.class);
        verify(submissionService, atLeastOnce())
                .createReference(eq(ProblemSeeder.SEED_AUTHOR_UUID), captor.capture(), eq(true));
        Map<Integer, SubmissionCommands.Create> byId = new HashMap<>();
        captor.getAllValues().forEach(r -> byId.put(r.problemId(), r));
        return byId;
    }

    /**
     * Converts one test case's JSON argument array into Java arguments, typed by the signature —
     * what the generated harness's JsonMini does on the judge, done here with Jackson.
     */
    private Object[] toArguments(JsonNode input, List<ProblemCommands.MethodParameterInput> parameters) throws Exception {
        assertThat(input.isArray() && input.size() == parameters.size())
                .as("%s carries one value per parameter", input).isTrue();
        Object[] args = new Object[parameters.size()];
        for (ProblemCommands.MethodParameterInput p : parameters) {
            args[p.position()] = json.treeToValue(input.get(p.position()), javaType(p.type()));
        }
        return args;
    }

    /** Exhaustive on purpose (no default) — a new ParamType must be taught here before it can be seeded. */
    private static Class<?> javaType(ParamType type) {
        return switch (type) {
            case INT -> int.class;
            case LONG -> long.class;
            case DOUBLE -> double.class;
            case BOOLEAN -> boolean.class;
            case STRING -> String.class;
            case INT_ARRAY -> int[].class;
            case DOUBLE_ARRAY -> double[].class;
            case BOOLEAN_ARRAY -> boolean[].class;
            case STRING_ARRAY -> String[].class;
            case INT_MATRIX -> int[][].class;
        };
    }

    /**
     * Compiles a seed solution the way the Java harness sees it — with the harness prelude's
     * {@code import java.util.*;} in front — using the JDK's own compiler ({@code javax.tools}), and
     * returns the problem's method on the resulting {@code Solution} class, made accessible (LeetCode's
     * {@code class Solution} is package-private). Runs on the test JDK, not Judge0's OpenJDK 13; that
     * one is exercised only by {@code LanguageHarnessExecutionIT}.
     */
    private static Method compileMethod(String source, ProblemCommands.Create problem, Path dir) throws Exception {
        Path file = dir.resolve("Solution.java");
        Files.writeString(file, "import java.util.*;\n" + source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler.run(null, null, null, "-d", dir.toString(), file.toString()))
                .as("'%s': the reference solution compiles", problem.title()).isZero();
        URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, ProblemSeederTest.class.getClassLoader());
        Class<?>[] parameterTypes = problem.parameters().stream()
                .map(p -> javaType(p.type())).toArray(Class<?>[]::new);
        Method method = loader.loadClass("Solution").getMethod(problem.methodName(), parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
