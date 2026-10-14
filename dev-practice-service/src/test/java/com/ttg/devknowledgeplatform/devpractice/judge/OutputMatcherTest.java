package com.ttg.devknowledgeplatform.devpractice.judge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.devpractice.enums.OutputOrder;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * Unit tests for {@link OutputMatcher}'s structural, return-type-aware comparison — in particular
 * the two cases a plain {@code JsonNode.equals} got wrong: whole-number doubles rendered
 * differently per language, and floating-point rounding error.
 */
class OutputMatcherTest {

    private final OutputMatcher matcher = new OutputMatcher(new ObjectMapper());

    @ParameterizedTest(name = "{2}: {0} matches {1}")
    @CsvSource(delimiter = '|', value = {
            "[0, 1]                  | [0,1]                  | INT_ARRAY",
            "2                       | 2.0                    | INT",
            "9007199254740993        | 9007199254740993       | LONG",
            "2                       | 2.0                    | DOUBLE",
            "0.30000000000000004     | 0.3                    | DOUBLE",
            "[1, 2.5]                | [1.0,2.5]              | DOUBLE_ARRAY",
            "100000.5                | 100000.50001           | DOUBLE",
            "\"a b\"                 | \"a b\"                | STRING",
            "[[1,2],[]]              | [[1, 2], []]           | INT_MATRIX",
            "[[\"a\"], []]           | [[\"a\"],[]]           | STRING_LIST_LIST",
            "[true,false]            | [true, false]          | BOOLEAN_ARRAY",
    })
    void accepts(String actual, String expected, ParamType returnType) {
        assertThat(matcher.matches(actual, expected, AnswerSpec.ordered(returnType))).isTrue();
    }

    @ParameterizedTest(name = "{2}: {0} does not match {1}")
    @CsvSource(delimiter = '|', value = {
            "[1, 0]                  | [0,1]                  | INT_ARRAY",
            "[0]                     | [0,1]                  | INT_ARRAY",
            "2                       | 2.5                    | INT",
            "9007199254740992        | 9007199254740993       | LONG",
            "0.3001                  | 0.3                    | DOUBLE",
            "\"1\"                   | 1                      | INT",
            "true                    | 1                      | BOOLEAN",
            "[[1,2]]                 | [[1,2],[]]             | INT_MATRIX",
    })
    void rejects(String actual, String expected, ParamType returnType) {
        assertThat(matcher.matches(actual, expected, AnswerSpec.ordered(returnType))).isFalse();
    }

    @ParameterizedTest(name = "any order: {0} matches {1}")
    @CsvSource(delimiter = '|', value = {
            "[3,2]                                  | [2,3]",
            "[2, 3]                                 | [2,3]",
            "[-1,10,2]                              | [2,-1,10]",
            "[1.0,2]                                | [2,1]",
            // Nested: groups in any order, and each group's words in any order.
            "[[\"tops\",\"pots\"],[\"x\"]]          | [[\"x\"],[\"pots\",\"tops\"]]",
            "[[\"b\"],[\"a\",\"c\"],[\"c\",\"a\"]]  | [[\"c\",\"a\"],[\"a\",\"c\"],[\"b\"]]",
            "[[\"\"]]                               | [[\"\"]]",
            "[]                                     | []",
    })
    void anyOrderAcceptsEveryPermutationAtEveryLevel(String actual, String expected) {
        assertThat(matcher.matches(actual, expected, new AnswerSpec(ParamType.STRING_ARRAY, OutputOrder.ANY_ORDER))).isTrue();
    }

    @ParameterizedTest(name = "any order: {0} doesn't match {1}")
    @CsvSource(delimiter = '|', value = {
            // Duplicates still count — sorted, not compared as sets.
            "[1,1,2]                                | [1,2,2]",
            "[1,2]                                  | [1,2,2]",
            "[2,3,4]                                | [2,3]",
            // Regrouping the same words is a different answer.
            "[[\"a\",\"b\"],[\"c\"]]                | [[\"a\"],[\"b\",\"c\"]]",
            "[[\"a\"],[\"b\"]]                      | [[\"a\",\"b\"]]",
            "[\"1\",\"2\"]                          | [1,2]",
    })
    void anyOrderStillRejectsADifferentMultiset(String actual, String expected) {
        assertThat(matcher.matches(actual, expected, new AnswerSpec(ParamType.STRING_ARRAY, OutputOrder.ANY_ORDER))).isFalse();
    }

    @Test
    void orderedStillRejectsAPermutation() {
        assertThat(matcher.matches("[3,2]", "[2,3]", AnswerSpec.ordered(ParamType.INT_ARRAY))).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedOutput() {
        assertThat(matcher.matches(null, "[0,1]", AnswerSpec.ordered(ParamType.INT_ARRAY))).isFalse();
        assertThat(matcher.matches("", "[0,1]", AnswerSpec.ordered(ParamType.INT_ARRAY))).isFalse();
        assertThat(matcher.matches("Traceback (most recent call last)", "[0,1]", AnswerSpec.ordered(ParamType.INT_ARRAY))).isFalse();
    }
}
