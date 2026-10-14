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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
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
import com.ttg.devknowledgeplatform.devpractice.harness.JavaScriptLanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaScriptSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarness;
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
        // Each run goes to the id its own problem was created with — not to some other problem. A whole
        // word, not "name(": JavaScript declares it as `var evalRPN = function(...)`.
        runsById.values().forEach(run -> assertThat(run.command().sourceCode())
                .containsPattern("\\b" + problemsById.get(run.command().problemId()).methodName() + "\\b"));
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
     * A language whose seed implementations run as the <em>real</em> judge program under a local
     * interpreter: the harness wraps the method exactly as it does for Judge0.
     *
     * @param harness       builds the program Judge0 would receive
     * @param extension     the program file's extension
     * @param candidates    launch commands to try in order (the first that reports {@code versionPrefix} wins)
     * @param versionPrefix what {@code <command> --version} must print, to reject e.g. a Python 2
     */
    private record LocalRuntime(ProgrammingLanguage language, LanguageHarness harness, String extension,
                                List<List<String>> candidates, String versionPrefix) { }

    /** Every language not run in-process like Java. A new language needs an entry here (see the guard test). */
    private static final Map<ProgrammingLanguage, LocalRuntime> LOCAL_RUNTIMES = Map.of(
            ProgrammingLanguage.PYTHON, new LocalRuntime(ProgrammingLanguage.PYTHON, new PythonLanguageHarness(), ".py",
                    List.of(List.of("python3"), List.of("python"), List.of("py", "-3")), "Python 3"),
            ProgrammingLanguage.JAVASCRIPT, new LocalRuntime(ProgrammingLanguage.JAVASCRIPT, new JavaScriptLanguageHarness(), ".js",
                    List.of(List.of("node")), "v"));

    /**
     * Python implementations run as the real {@link PythonLanguageHarness} program under a local
     * Python 3. Skipped (not failed) when none is installed; the code still can't reach learners
     * unverified, since only runs the real judge ACCEPTED are ever shown.
     */
    @Test
    void everyPythonImplementationPassesEveryTestCase(@TempDir Path dir) throws Exception {
        assertPassesUnderLocalRuntime(LOCAL_RUNTIMES.get(ProgrammingLanguage.PYTHON), dir);
    }

    /** JavaScript implementations run as the real {@link JavaScriptLanguageHarness} program under a local Node.js; skipped without one. */
    @Test
    void everyJavaScriptImplementationPassesEveryTestCase(@TempDir Path dir) throws Exception {
        assertPassesUnderLocalRuntime(LOCAL_RUNTIMES.get(ProgrammingLanguage.JAVASCRIPT), dir);
    }

    /**
     * Every language has a runner here — Java in-process, the rest through {@link #LOCAL_RUNTIMES} — so
     * no seed implementation can be checked by nothing. Fails when a {@link ProgrammingLanguage} is added
     * without one.
     */
    @Test
    void everyLanguageHasALocalRunner() {
        assertThat(EnumSet.complementOf(EnumSet.of(ProgrammingLanguage.JAVA)))
                .as("add a LocalRuntime (and a test calling it) for each new language")
                .isEqualTo(LOCAL_RUNTIMES.keySet());
    }

    /**
     * Writes each implementation in {@code runtime}'s language as the real judge program and runs it
     * once per test case, compared by the judge's own {@link OutputMatcher}. The cases run concurrently,
     * reporting every failure but stopping at a timeout (see {@link CollectFailuresScope}). Skipped
     * when the interpreter is missing — but only after asserting the seed files carry such
     * implementations, so an empty language can't pass vacuously on a machine that has the interpreter.
     */
    private void assertPassesUnderLocalRuntime(LocalRuntime runtime, Path dir) throws Exception {
        seeder.seed();
        List<SeededRun> runs = runsIn(runtime.language());
        assertThat(runs).as("seed files carry %s implementations", runtime.language()).isNotEmpty();
        List<String> command = localCommand(runtime);
        Assumptions.assumeTrue(command != null,
                "no local " + runtime.language() + " runtime — its seed implementations not run");

        Map<Integer, ProblemCommands.Create> problemsById = problemsById();
        OutputMatcher matcher = new OutputMatcher(json);
        // Programs are written up front, on this thread; only the process runs fan out.
        List<Callable<CaseOutcome>> cases = new ArrayList<>();
        int i = 0;
        for (SeededRun run : runs) {
            ProblemCommands.Create problem = problemsById.get(run.command().problemId());
            Path program = dir.resolve("program" + i++ + runtime.extension());
            Files.writeString(program, runtime.harness().buildProgram(toEntity(problem), run.command().sourceCode()));
            int c = 0;
            for (ProblemCommands.TestCaseInput testCase : problem.testCases()) {
                // Own directory per case: concurrent cases of one program must not share a stdin file.
                Path caseDir = Files.createDirectory(dir.resolve(program.getFileName() + "-case" + c++));
                String label = "%s (%s): %s(%s)".formatted(problem.title(), runtime.language(),
                        problem.methodName(), testCase.input());
                int index = cases.size();
                cases.add(() -> judgeCase(index, label, runProgram(command, program, caseDir, testCase.input()),
                        testCase.expectedOutput(), problem.returnType(), matcher));
            }
        }

        assertEveryCasePasses(cases);
    }

    /** How one test-case process ended — a sealed hierarchy so {@link #judgeCase} must handle both. */
    private sealed interface ProcessResult {
        /** Killed after {@link #PROCESS_TIMEOUT}. */
        record TimedOut() implements ProcessResult { }

        /** Ran to completion, with whatever it printed. */
        record Exited(int exitCode, String stdout, String stderr) implements ProcessResult { }
    }

    /** Per process, generous for a cold interpreter start on a loaded machine. */
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(10);

    /**
     * One case's verdict, as its subtask returns it. Sealed, so {@link CollectFailuresScope}'s policy
     * switch must decide what every kind of outcome does to the run. {@code index} is the case's
     * position, so failures can be reported in case order whatever order they finished in.
     */
    private sealed interface CaseOutcome {
        int index();

        /** Printed the expected answer. */
        record Passed(int index) implements CaseOutcome { }

        /** Wrong output or a runtime error — cheap (the process already exited), so the run continues. */
        record Failed(int index, String message) implements CaseOutcome { }

        /** Killed after {@link #PROCESS_TIMEOUT} — expensive, and likely to repeat, so the run stops. */
        record TimedOut(int index, String message) implements CaseOutcome { }
    }

    /** Turns how one case's process ended into its verdict. */
    private static CaseOutcome judgeCase(int index, String label, ProcessResult result, String expectedOutput,
                                         ParamType returnType, OutputMatcher matcher) {
        return switch (result) {
            case ProcessResult.TimedOut t ->
                    new CaseOutcome.TimedOut(index, label + " timed out after " + PROCESS_TIMEOUT.toSeconds() + " s");
            case ProcessResult.Exited(int exitCode, String stdout, String stderr) when exitCode != 0 ->
                    new CaseOutcome.Failed(index, label + " exited with " + exitCode + ":" + System.lineSeparator() + stderr.strip());
            case ProcessResult.Exited(int exitCode, String stdout, String stderr) ->
                    matcher.matches(stdout, expectedOutput, returnType) ? new CaseOutcome.Passed(index)
                            : new CaseOutcome.Failed(index, label + " printed " + stdout.strip() + ", expected " + expectedOutput);
        };
    }

    /**
     * Runs every case as a subtask of one {@link CollectFailuresScope} (a {@link StructuredTaskScope},
     * JDK 21 preview, JEP 453 — hence {@code --enable-preview} for this module's test compile and
     * surefire) and fails listing every wrong answer — unless a case times out, which stops the rest.
     * The scope owns its threads' lifetime: none outlives the {@code try} block.
     *
     * <p>Subtasks are virtual threads (the scope's default factory). Each case spends nearly all its
     * time waiting on a child process, which is the I/O-bound shape virtual threads suit — but they make
     * <em>threads</em> cheap, not processes: the semaphore caps live interpreters at the core count.
     *
     * @throws AssertionError listing the failures (see {@link CollectFailuresScope#throwIfAnyFailed})
     */
    private static void assertEveryCasePasses(List<Callable<CaseOutcome>> cases) throws InterruptedException {
        Semaphore processSlots = new Semaphore(Runtime.getRuntime().availableProcessors());
        try (var scope = new CollectFailuresScope()) {
            for (Callable<CaseOutcome> testCase : cases) {
                scope.fork(() -> {
                    processSlots.acquire();
                    try {
                        return testCase.call();
                    } finally {
                        processSlots.release();
                    }
                });
            }
            scope.join().throwIfAnyFailed();
        }
    }

    /**
     * A custom {@link StructuredTaskScope} policy, between the JDK's two built-ins:
     * {@code ShutdownOnFailure} stops at the first failure (one message, the rest unknown) and
     * {@code ShutdownOnSuccess} at the first success (useless here). This one keeps going through
     * cheap failures, collecting them all, and shuts down only on what is expensive to repeat — a
     * timeout (each costs {@link #PROCESS_TIMEOUT}, and a hang usually hangs every case) or a subtask
     * that threw (the test's own machinery is broken; more cases won't say anything new).
     *
     * <p>The whole policy is {@link #handleComplete}, the scope's extension point: the JDK calls it
     * on each subtask's <em>own</em> thread as it completes — concurrently, hence the concurrent
     * collections — and stops calling it once the scope is shut down, so the cancelled siblings'
     * interrupted results never reach it.
     */
    private static final class CollectFailuresScope extends StructuredTaskScope<CaseOutcome> {
        private final Queue<CaseOutcome> failures = new ConcurrentLinkedQueue<>();
        private final Queue<Throwable> crashes = new ConcurrentLinkedQueue<>();
        private final AtomicInteger completed = new AtomicInteger();
        /** Only the owner thread forks, so a plain field is enough. */
        private int forked;

        @Override
        public <U extends CaseOutcome> Subtask<U> fork(Callable<? extends U> task) {
            forked++;
            return super.fork(task);
        }

        @Override
        protected void handleComplete(Subtask<? extends CaseOutcome> subtask) {
            super.handleComplete(subtask);
            completed.incrementAndGet();
            switch (subtask.state()) {
                case SUCCESS -> {
                    switch (subtask.get()) {
                        case CaseOutcome.Passed passed -> { }
                        case CaseOutcome.Failed failed -> failures.add(failed);
                        case CaseOutcome.TimedOut timedOut -> {
                            failures.add(timedOut);
                            shutdown();
                        }
                    }
                }
                case FAILED -> {
                    crashes.add(subtask.exception());
                    shutdown();
                }
                // Never passed to handleComplete: UNAVAILABLE means not completed (or completed after shutdown).
                case UNAVAILABLE -> throw new IllegalStateException("incomplete subtask " + subtask);
            }
        }

        /** Covariant override, so {@code scope.join().throwIfAnyFailed()} reads as one step. */
        @Override
        public CollectFailuresScope join() throws InterruptedException {
            super.join();
            return this;
        }

        /**
         * Fails with every collected failure in case order, noting how many cases a shutdown cancelled;
         * a crash comes first, with any further crashes attached as suppressed.
         *
         * @throws AssertionError if any case failed, timed out or crashed
         * @throws IllegalStateException if called before {@link #join} or by another thread than the owner
         */
        void throwIfAnyFailed() {
            ensureOwnerAndJoined();
            if (!crashes.isEmpty()) {
                AssertionError error = new AssertionError("a case crashed", crashes.peek());
                crashes.stream().skip(1).forEach(error::addSuppressed);
                throw error;
            }
            if (failures.isEmpty()) {
                return;
            }
            List<String> messages = new ArrayList<>(failures.stream()
                    .sorted(Comparator.comparingInt(CaseOutcome::index))
                    .map(outcome -> switch (outcome) {
                        case CaseOutcome.Failed failed -> failed.message();
                        case CaseOutcome.TimedOut timedOut -> timedOut.message();
                        case CaseOutcome.Passed passed -> throw new IllegalStateException("collected a pass");
                    })
                    .toList());
            int cancelled = forked - completed.get();
            if (cancelled > 0) {
                messages.add(cancelled + " more case(s) cancelled after the timeout — results unknown");
            }
            throw new AssertionError(failures.size() + " failure(s):" + System.lineSeparator()
                    + String.join(System.lineSeparator(), messages));
        }
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

    /** The first of the runtime's launch commands that works on this machine, or null. */
    private static List<String> localCommand(LocalRuntime runtime) {
        for (List<String> candidate : runtime.candidates()) {
            try {
                List<String> command = new ArrayList<>(candidate);
                command.add("--version");
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
                        && version.startsWith(runtime.versionPrefix())) {
                    return candidate;
                }
            } catch (Exception e) {
                // not installed under this name — try the next
            }
        }
        return null;
    }

    /**
     * Runs the program with {@code stdin} as its input. stdout/stderr go to files in {@code caseDir},
     * not pipes: reading a pipe blocks until the process exits, so a hung program would never reach the
     * timeout, and a full stderr pipe could stall it while stdout was being read.
     */
    private static ProcessResult runProgram(List<String> launch, Path program, Path caseDir, String stdin)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(launch);
        command.add(program.toString());
        Path input = Files.writeString(caseDir.resolve("stdin"), stdin, StandardCharsets.UTF_8);
        Path stdout = caseDir.resolve("stdout");
        Path stderr = caseDir.resolve("stderr");
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectInput(input.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        // Windows consoles otherwise default to a legacy code page for Python's stdio (Node ignores it).
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        Process process = builder.start();
        try {
            if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                return new ProcessResult.TimedOut();
            }
            return new ProcessResult.Exited(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            // Timed out, or interrupted by the scope's shutdown: an interrupt stops the wait, never the
            // child process, so kill it here or it outlives the test. Wait for the kill (join(), which
            // ignores interrupts) so Windows releases the output files before @TempDir cleanup.
            if (process.isAlive()) {
                process.destroyForcibly().onExit().join();
            }
        }
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
