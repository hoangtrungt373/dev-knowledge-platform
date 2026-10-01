package com.ttg.devknowledgeplatform.devpractice.service.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaScriptSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.JavaSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.PythonSignatureTemplateParser;
import com.ttg.devknowledgeplatform.devpractice.harness.SignatureTemplateParserRegistry;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

/**
 * Loads the real {@code data/problems/*.md} seed files through {@link ProblemSeeder} (real template
 * parsers; repositories and {@link ProblemService} mocked) and checks what would be created —
 * including running every Evaluate Reverse Polish Notation test case through a reference solution,
 * so a wrong {@code expectedOutput} in the seed file fails the build rather than every user's
 * correct submission.
 */
class ProblemSeederTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ProblemTagRepository tagRepository = mock(ProblemTagRepository.class);
    private final ProblemService problemService = mock(ProblemService.class);
    private final SlugService slugService = mock(SlugService.class);
    private final ProblemSeeder seeder = new ProblemSeeder(problemRepository, tagRepository, problemService,
            new SignatureTemplateParserRegistry(List.of(new JavaSignatureTemplateParser(),
                    new PythonSignatureTemplateParser(), new JavaScriptSignatureTemplateParser())),
            slugService);
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
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
        assertThat(command.status()).isEqualTo(ContentStatus.PUBLISHED);
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
    void everyTestCaseMatchesAReferenceSolution() throws Exception {
        seeder.seed();

        for (ProblemCommands.TestCaseInput testCase : captureCreated().testCases()) {
            JsonNode args = json.readTree(testCase.input());
            assertThat(args.isArray() && args.size() == 1).as("one argument: %s", testCase.input()).isTrue();
            String[] tokens = json.treeToValue(args.get(0), String[].class);
            assertThat(evalRPN(tokens)).as("evalRPN(%s)", testCase.input())
                    .isEqualTo(Integer.parseInt(testCase.expectedOutput()));
        }
    }

    @Test
    void skipsAProblemThatAlreadyExists() {
        when(problemRepository.existsBySlug("evaluate-reverse-polish-notation")).thenReturn(true);

        assertThat(seeder.seed()).isZero();
        verify(problemService, never()).create(any(), anyString());
    }

    private ProblemCommands.Create captureCreated() {
        ArgumentCaptor<ProblemCommands.Create> captor = ArgumentCaptor.forClass(ProblemCommands.Create.class);
        verify(problemService).create(captor.capture(), eq(ProblemSeeder.SEED_AUTHOR_UUID));
        return captor.getValue();
    }

    /** Reference solution — Java's int division already truncates toward zero, as the problem requires. */
    private static int evalRPN(String[] tokens) {
        Deque<Integer> stack = new ArrayDeque<>();
        for (String token : tokens) {
            switch (token) {
                case "+" -> stack.push(stack.pop() + stack.pop());
                case "*" -> stack.push(stack.pop() * stack.pop());
                case "-" -> { int b = stack.pop(); stack.push(stack.pop() - b); }
                case "/" -> { int b = stack.pop(); stack.push(stack.pop() / b); }
                default -> stack.push(Integer.parseInt(token));
            }
        }
        return stack.pop();
    }
}
