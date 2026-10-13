package com.ttg.devknowledgeplatform.devpractice.service.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.editorial.CodePlaceholders;
import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaScriptSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.PythonLanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.harness.PythonSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.SignatureTemplateParserRegistry;
import com.ttg.devknowledgeplatform.devpractice.judge.OutputMatcher;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionService;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

/**
 * Loads the real {@code data/problems/*.md} seed files through {@link ProblemSeeder} (real template
 * parsers; repositories and services mocked) and checks what would be created. The central check is
 * generic over every seed file: every implementation in its {@code solutions} is compiled and run
 * against each of its test cases, so a wrong {@code expectedOutput} (or a broken implementation) fails
 * the build here rather than leaving the problem stuck as a draft — or an approach without verified
 * code — when the real judge rejects it. A new seed file is
 * covered automatically — no per-problem test needed unless it adds a {@link ParamType} the argument
 * conversion below doesn't handle yet (the exhaustive switch then fails to compile).
 */
class ProblemSeederTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ProblemTagRepository tagRepository = mock(ProblemTagRepository.class);
    private final ProblemService problemService = mock(ProblemService.class);
    private final SubmissionService submissionService = mock(SubmissionService.class);
    private final ProblemSolutionService problemSolutionService = mock(ProblemSolutionService.class);
    private final SlugService slugService = mock(SlugService.class);
    private final ProblemSeeder seeder = new ProblemSeeder(problemRepository, tagRepository, problemService, submissionService, problemSolutionService,
            new SignatureTemplateParserRegistry(List.of(new JavaSignatureTemplateParser(),
                    new PythonSignatureTemplateParser(), new JavaScriptSignatureTemplateParser())),
            slugService);
    private final ObjectMapper json = new ObjectMapper();

    /** Ids handed out by the mocked ProblemService#create, in creation order. */
    private final AtomicInteger nextId = new AtomicInteger(100);

    /** Every reference run the seeder submitted, by the id the mocked createReference handed out. */
    private final Map<Integer, SeededRun> runsById = new HashMap<>();
    private final AtomicInteger nextRunId = new AtomicInteger(500);

    /** One createReference call: which problem, the code, and whether it may publish the problem. */
    private record SeededRun(SubmissionCommands.Create command, boolean publishOnAccept) {
    }

    @BeforeEach
    void setUp() {
        when(problemService.create(any(), anyString())).thenAnswer(inv -> {
            Problem saved = Problem.builder().build();
            saved.setId(nextId.getAndIncrement());
            return saved;
        });
        when(submissionService.createReference(anyString(), any(), anyBoolean())).thenAnswer(inv -> {
            Submission run = Submission.builder().build();
            run.setId(nextRunId.getAndIncrement());
            runsById.put(run.getId(), new SeededRun(inv.getArgument(1), inv.getArgument(2)));
            return run;
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
    void eachPublishedProblemGetsExactlyOnePublishOnAcceptRun() {
        seeder.seed();

        // Exactly one, never all: publishing shares the verdict's transaction, so two publish-on-accept
        // runs accepted together would race on the problem row and one verdict would roll back.
        Map<Integer, Long> publishingRunsByProblem = runsById.values().stream()
                .filter(SeededRun::publishOnAccept)
                .collect(Collectors.groupingBy(r -> r.command().problemId(), Collectors.counting()));
        Map<Integer, ProblemCommands.Create> problemsById = problemsById();
        assertThat(publishingRunsByProblem.keySet()).isEqualTo(problemsById.keySet());
        assertThat(publishingRunsByProblem.values()).containsOnly(1L);
        // Each run goes to the id its own problem was created with — not to some other problem.
        runsById.values().forEach(run -> assertThat(run.command().sourceCode())
                .contains(" " + problemsById.get(run.command().problemId()).methodName() + "("));
    }

    @Test
    void everyJavaImplementationPassesEveryTestCase(@TempDir Path dir) throws Exception {
        seeder.seed();
        Map<Integer, ProblemCommands.Create> problemsById = problemsById();
        List<SeededRun> javaRuns = runsIn(ProgrammingLanguage.JAVA);
        assertThat(javaRuns).as("seed files carry Java implementations").isNotEmpty();

        int i = 0;
        for (SeededRun run : javaRuns) {
            ProblemCommands.Create problem = problemsById.get(run.command().problemId());
            assertPassesEveryTestCase(run.command().sourceCode(), problem, Files.createDirectory(dir.resolve("run" + i++)));
        }
    }

    /**
     * Python implementations run as the <em>real</em> judge program — {@link PythonLanguageHarness}
     * wraps the method exactly as it does for Judge0 — under a local interpreter, one process per test
     * case, compared by the judge's own {@link OutputMatcher}. Skipped (not failed) when no Python 3 is
     * installed; the code still can't reach learners unverified, since only runs the real judge
     * ACCEPTED are ever shown.
     */
    @Test
    void everyPythonImplementationPassesEveryTestCase(@TempDir Path dir) throws Exception {
        seeder.seed();
        List<SeededRun> pythonRuns = runsIn(ProgrammingLanguage.PYTHON);
        assertThat(pythonRuns).as("seed files carry Python implementations").isNotEmpty();
        List<String> python = localPython();
        Assumptions.assumeTrue(python != null, "no local Python 3 interpreter — Python seed implementations not run");

        Map<Integer, ProblemCommands.Create> problemsById = problemsById();
        OutputMatcher matcher = new OutputMatcher(json);
        int i = 0;
        for (SeededRun run : pythonRuns) {
            ProblemCommands.Create problem = problemsById.get(run.command().problemId());
            Path program = dir.resolve("program" + i++ + ".py");
            Files.writeString(program, new PythonLanguageHarness().buildProgram(toEntity(problem), run.command().sourceCode()));
            for (ProblemCommands.TestCaseInput testCase : problem.testCases()) {
                String stdout = runPython(python, program, testCase.input());
                assertThat(matcher.matches(stdout, testCase.expectedOutput(), problem.returnType()))
                        .as("%s (Python): %s(%s) printed %s, expected %s", problem.title(), problem.methodName(),
                                testCase.input(), stdout.strip(), testCase.expectedOutput())
                        .isTrue();
            }
        }
    }

    /** A seed implementation in a language the two tests above can't run would be checked by nothing here. */
    @Test
    void everyImplementationIsInALanguageThisTestCanRun() {
        seeder.seed();

        assertThat(runsById.values()).extracting(r -> r.command().language())
                .allMatch(l -> l == ProgrammingLanguage.JAVA || l == ProgrammingLanguage.PYTHON,
                        "JAVA or PYTHON — add a local runner here before seeding another language");
    }

    private List<SeededRun> runsIn(ProgrammingLanguage language) {
        return runsById.values().stream().filter(r -> r.command().language() == language).toList();
    }

    /** The parts of a problem the harness reads — method name, return type, ordered parameters. */
    private static Problem toEntity(ProblemCommands.Create command) {
        Problem problem = Problem.builder().methodName(command.methodName()).returnType(command.returnType()).build();
        for (ProblemCommands.MethodParameterInput p : command.parameters()) {
            problem.getParameters().add(MethodParameter.builder()
                    .problem(problem).name(p.name()).type(p.type()).position(p.position()).build());
        }
        return problem;
    }

    /** The first working Python 3 launch command on this machine, or null. */
    private static List<String> localPython() {
        for (List<String> candidate : List.of(List.of("python3"), List.of("python"), List.of("py", "-3"))) {
            try {
                List<String> command = new ArrayList<>(candidate);
                command.add("--version");
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0 && version.startsWith("Python 3")) {
                    return candidate;
                }
            } catch (Exception e) {
                // not installed under this name — try the next
            }
        }
        return null;
    }

    /** Runs the program with {@code stdin} as its input; fails on a non-zero exit or a timeout. */
    private static String runPython(List<String> python, Path program, String stdin) throws Exception {
        List<String> command = new ArrayList<>(python);
        command.add(program.toString());
        Path input = Files.writeString(program.resolveSibling(program.getFileName() + ".in"), stdin, StandardCharsets.UTF_8);
        ProcessBuilder builder = new ProcessBuilder(command).redirectInput(input.toFile());
        // Windows consoles otherwise default to a legacy code page for Python's stdio.
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("Python timed out on %s", stdin).isTrue();
        assertThat(process.exitValue()).as("Python failed on %s:%n%s", stdin, stderr).isZero();
        return stdout;
    }

    @Test
    void aFileStillUsingTheRetiredReferenceSolutionKeyFailsLoudly() {
        assertThatThrownBy(() -> seeder.seed("classpath*:problems-invalid/legacy-reference-solution.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy-reference-solution.md")
                .hasMessageContaining("'referenceSolution' is no longer supported");
    }

    @Test
    void aPublishedFileWithoutAnyImplementationFailsLoudly() {
        assertThatThrownBy(() -> seeder.seed("classpath*:problems-invalid/published-without-implementation.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published-without-implementation.md")
                .hasMessageContaining("needs at least one solution implementation");
        verify(problemService, never()).create(any(), anyString());
    }

    /** Every created problem's command by the id the mocked ProblemService#create handed out. */
    private Map<Integer, ProblemCommands.Create> problemsById() {
        Map<Integer, ProblemCommands.Create> byId = new HashMap<>();
        List<ProblemCommands.Create> created = createdInOrder();
        for (int i = 0; i < created.size(); i++) {
            byId.put(100 + i, created.get(i));
        }
        return byId;
    }

    @Test
    void seedsEachFilesSolutionsInOrderLinkedToTheirReferenceRuns() {
        seeder.seed();
        Map<String, Integer> idsByTitle = new HashMap<>();
        List<ProblemCommands.Create> created = createdInOrder();
        for (int i = 0; i < created.size(); i++) {
            idsByTitle.put(created.get(i).title(), 100 + i);
        }

        List<ProblemSolutionCommands.Create> duplicate = solutionsFor(idsByTitle.get("Contains Duplicate"));
        assertThat(duplicate).extracting(ProblemSolutionCommands.Create::title).containsExactly("Sorting", "Hash Set");
        // Each approach links its own run: two different solutions, even though both explanations say
        // {{code:JAVA}} — a placeholder resolves within its own approach.
        SeededRun sorting = runsById.get(duplicate.get(0).referenceSubmissionIds().get(0));
        SeededRun hashSet = runsById.get(duplicate.get(1).referenceSubmissionIds().get(0));
        assertThat(sorting.command().problemId()).isEqualTo(idsByTitle.get("Contains Duplicate"));
        assertThat(sorting.command().sourceCode()).contains("Arrays.sort").doesNotContain("HashSet");
        assertThat(hashSet.command().sourceCode()).contains("seen.add").doesNotContain("Arrays.sort");
        // The first implementation listed is the one that publishes the problem.
        assertThat(sorting.publishOnAccept()).isTrue();
        assertThat(hashSet.publishOnAccept()).isFalse();
        assertThat(duplicate).allSatisfy(s -> assertThat(s.explanation()).contains("**Complexity**"));

        List<ProblemSolutionCommands.Create> rpn = solutionsFor(idsByTitle.get("Evaluate Reverse Polish Notation"));
        assertThat(rpn).extracting(ProblemSolutionCommands.Create::title).containsExactly("Stack");
        assertThat(runsById.get(rpn.get(0).referenceSubmissionIds().get(0)).publishOnAccept()).isTrue();
    }

    @Test
    void everySeededExplanationPassesThePlaceholderRules() {
        seeder.seed();
        // ProblemSolutionService is mocked here, so run its explanation check against each seed
        // approach directly — a hand-written code block or a placeholder without a matching
        // implementation then fails the build, not a real startup.
        ArgumentCaptor<ProblemSolutionCommands.Create> captor = ArgumentCaptor.forClass(ProblemSolutionCommands.Create.class);
        verify(problemSolutionService, atLeastOnce()).create(any(), captor.capture());
        for (ProblemSolutionCommands.Create approach : captor.getAllValues()) {
            Set<ProgrammingLanguage> languages = approach.referenceSubmissionIds().stream()
                    .map(id -> runsById.get(id).command().language())
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProgrammingLanguage.class)));
            CodePlaceholders.validate(approach.explanation(), languages);
            assertThat(approach.explanation()).as("'%s' shows its code via a placeholder", approach.title())
                    .contains("{{code:JAVA}}");
        }
    }

    /** A problem's solution-create calls, in the order the seeder made them. */
    private List<ProblemSolutionCommands.Create> solutionsFor(Integer problemId) {
        ArgumentCaptor<ProblemSolutionCommands.Create> captor = ArgumentCaptor.forClass(ProblemSolutionCommands.Create.class);
        verify(problemSolutionService, atLeastOnce()).create(eq(problemId), captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    /** Compiles {@code source} (a class named Solution) and runs it on every test case of {@code problem}. */
    private void assertPassesEveryTestCase(String source, ProblemCommands.Create problem, Path dir) throws Exception {
        // Every seed solution is a class named Solution — one directory (and class loader) each.
        Method method = compileMethod(source, problem, dir);
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

    @Test
    void skipsProblemsThatAlreadyExist() {
        when(problemRepository.existsBySlug(anyString())).thenReturn(true);

        assertThat(seeder.seed()).isZero();
        verify(problemService, never()).create(any(), anyString());
        verify(submissionService, never()).createReference(any(), any(), anyBoolean());
        verify(problemSolutionService, never()).create(any(), any());
    }

    private List<ProblemCommands.Create> createdInOrder() {
        ArgumentCaptor<ProblemCommands.Create> captor = ArgumentCaptor.forClass(ProblemCommands.Create.class);
        verify(problemService, atLeastOnce()).create(captor.capture(), eq(ProblemSeeder.SEED_AUTHOR_UUID));
        return captor.getAllValues();
    }

    private Map<String, ProblemCommands.Create> createdByTitle() {
        return createdInOrder().stream().collect(Collectors.toMap(ProblemCommands.Create::title, Function.identity()));
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
