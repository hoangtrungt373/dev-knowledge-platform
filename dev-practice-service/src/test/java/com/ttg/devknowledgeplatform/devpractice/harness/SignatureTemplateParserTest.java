package com.ttg.devknowledgeplatform.devpractice.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * The three {@link SignatureTemplateParser}s, through {@link SignatureTemplateParserRegistry}: the
 * reference "Evaluate Reverse Polish Notation" template in each language, a round trip through
 * every harness's own starter code, and the error messages an admin sees.
 */
class SignatureTemplateParserTest {

    private final SignatureTemplateParserRegistry registry = new SignatureTemplateParserRegistry(List.of(
            new JavaSignatureTemplateParser(), new PythonSignatureTemplateParser(), new JavaScriptSignatureTemplateParser()));

    // ── The reference problem, in all three languages ──────────────────────────

    @Test
    void readsTheJavaTemplate() {
        ParsedSignature s = registry.parse(ProgrammingLanguage.JAVA, """
                class Solution {
                    public int evalRPN(String[] tokens) {

                    }
                }
                """);

        assertThat(s.methodName()).isEqualTo("evalRPN");
        assertThat(s.returnType()).isEqualTo(new ParsedSignature.TypeChoice(ParamType.INT, List.of()));
        assertThat(s.parameters()).containsExactly(param("tokens", ParamType.STRING_ARRAY));
    }

    @Test
    void readsThePythonTemplateAndFlagsIntAsAmbiguous() {
        ParsedSignature s = registry.parse(ProgrammingLanguage.PYTHON, """
                from typing import List

                class Solution:
                    def evalRPN(self, tokens: List[str]) -> int:
                        pass
                """);

        assertThat(s.methodName()).isEqualTo("evalRPN");
        // Python's int is both INT and LONG.
        assertThat(s.returnType()).isEqualTo(new ParsedSignature.TypeChoice(ParamType.INT, List.of(ParamType.LONG)));
        assertThat(s.parameters()).containsExactly(param("tokens", ParamType.STRING_ARRAY));
    }

    @Test
    void readsTheJavaScriptTemplateAndFlagsNumberAsAmbiguous() {
        ParsedSignature s = registry.parse(ProgrammingLanguage.JAVASCRIPT, """
                /**
                 * @param {string[]} tokens
                 * @return {number}
                 */
                var evalRPN = function(tokens) {

                };
                """);

        assertThat(s.methodName()).isEqualTo("evalRPN");
        assertThat(s.returnType())
                .isEqualTo(new ParsedSignature.TypeChoice(ParamType.INT, List.of(ParamType.LONG, ParamType.DOUBLE)));
        assertThat(s.parameters()).containsExactly(param("tokens", ParamType.STRING_ARRAY));
    }

    // ── Round trip: every harness's own starter code parses back to its signature ─────

    static Stream<Arguments> starterCodeOfEveryHarness() {
        return HarnessFixtures.ALL_HARNESSES.stream().flatMap(harness -> Stream.of(
                Arguments.of(harness, HarnessFixtures.twoSum()),
                Arguments.of(harness, HarnessFixtures.everyParamType())));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("starterCodeOfEveryHarness")
    void parsesEveryHarnessesOwnStarterCodeBackToTheSameSignature(LanguageHarness harness, Problem problem) {
        ParsedSignature s = registry.parse(harness.language(), harness.renderStarterCode(problem));

        assertThat(s.methodName()).isEqualTo(problem.getMethodName());
        assertMeans(s.returnType(), problem.getReturnType());
        List<MethodParameter> expected = problem.getParameters().stream()
                .sorted(Comparator.comparing(MethodParameter::getPosition)).toList();
        assertThat(s.parameters()).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(s.parameters().get(i).name()).isEqualTo(expected.get(i).getName());
            assertMeans(s.parameters().get(i).type(), expected.get(i).getType());
        }
    }

    /** The original type is either the chosen one or offered as an alternative — never lost. */
    private static void assertMeans(ParsedSignature.TypeChoice choice, ParamType original) {
        assertThat(choice.alternatives().contains(original) || choice.type() == original)
                .as("%s should mean %s", choice, original).isTrue();
    }

    // ── Accepted variations ─────────────────────────────────────────────────────

    @Test
    void javaAcceptsABareMethodWithCommentsAnnotationsAndFinal() {
        ParsedSignature s = registry.parse(ProgrammingLanguage.JAVA, """
                // Implement me
                @SuppressWarnings("unused")
                public int[][] merge(final int[][] intervals, /* inline */ boolean strict) { return null; }
                """);

        assertThat(s.methodName()).isEqualTo("merge");
        assertThat(s.returnType().type()).isEqualTo(ParamType.INT_MATRIX);
        assertThat(s.parameters()).containsExactly(
                param("intervals", ParamType.INT_MATRIX), param("strict", ParamType.BOOLEAN));
    }

    @Test
    void pythonAcceptsPep585AndTypingPrefixesWithoutSelf() {
        ParsedSignature s = registry.parse(ProgrammingLanguage.PYTHON,
                "def search(nums: list[int], target: typing.List[ float ]) -> bool:\n    pass");

        assertThat(s.parameters()).containsExactly(
                param("nums", ParamType.INT_ARRAY), param("target", ParamType.DOUBLE_ARRAY));
        assertThat(s.returnType().type()).isEqualTo(ParamType.BOOLEAN);
    }

    @Test
    void javaScriptAcceptsFunctionDeclarationsArrayGenericsAndReturns() {
        ParsedSignature s = registry.parse(ProgrammingLanguage.JAVASCRIPT, """
                /**
                 * @param {Array<Array<number>>} grid - the board
                 * @returns {boolean}
                 */
                function isValid(grid) {}
                """);

        assertThat(s.parameters().get(0).type().type()).isEqualTo(ParamType.INT_MATRIX);
        assertThat(s.returnType().type()).isEqualTo(ParamType.BOOLEAN);
    }

    // ── Errors an admin sees ────────────────────────────────────────────────────

    @Test
    void namesTheUnsupportedTypeAndListsTheSupportedOnes() {
        assertThatThrownBy(() -> registry.parse(ProgrammingLanguage.JAVA,
                "class Solution { public int sum(List<Integer> nums) {} }"))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("Couldn't read the code template: unsupported type 'List<Integer>' for the parameter 'nums'")
                .hasMessageContaining("int[]");
    }

    @Test
    void rejectsATemplateWithSeveralMethods() {
        assertThatThrownBy(() -> registry.parse(ProgrammingLanguage.JAVA,
                "class Solution { public int a(int x) {} private int helper(int y) {} }"))
                .hasMessageContaining("found 2 methods (a, helper)");
    }

    @Test
    void rejectsAJavaMethodOutsideSolution() {
        assertThatThrownBy(() -> registry.parse(ProgrammingLanguage.JAVA, "class Main { public int a(int x) {} }"))
                .hasMessageContaining("it must be in `class Solution`");
    }

    @Test
    void rejectsAPythonParameterWithoutATypeHint() {
        assertThatThrownBy(() -> registry.parse(ProgrammingLanguage.PYTHON, "def twoSum(self, nums, target: int) -> int:"))
                .hasMessageContaining("parameter 'nums' has no type hint");
    }

    @Test
    void rejectsAPythonFunctionWithoutAReturnHint() {
        assertThatThrownBy(() -> registry.parse(ProgrammingLanguage.PYTHON, "def twoSum(self, nums: List[int]):"))
                .hasMessageContaining("has no return type hint");
    }

    @Test
    void rejectsAJavaScriptParameterWithoutJsDoc() {
        assertThatThrownBy(() -> registry.parse(ProgrammingLanguage.JAVASCRIPT, """
                /** @param {number[]} nums
                 *  @return {number} */
                var twoSum = function(nums, target) {};
                """))
                .hasMessageContaining("parameter 'target' has no `@param {type} target` JSDoc tag");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> registry.parse(ProgrammingLanguage.JAVA, "this is not code"))
                .hasMessageContaining("not valid Java");
    }

    private static ParsedSignature.ParsedParameter param(String name, ParamType type) {
        return new ParsedSignature.ParsedParameter(name, new ParsedSignature.TypeChoice(type, List.of()));
    }
}
