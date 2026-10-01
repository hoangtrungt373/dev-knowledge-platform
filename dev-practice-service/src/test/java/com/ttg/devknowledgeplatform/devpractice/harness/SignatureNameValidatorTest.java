package com.ttg.devknowledgeplatform.devpractice.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;

/**
 * Runs {@link SignatureNameValidator} against the real harnesses, so the reserved-name resource
 * files themselves are under test, not a hand-built stand-in.
 */
class SignatureNameValidatorTest {

    private final SignatureNameValidator validator = new SignatureNameValidator(List.of(
            new JavaLanguageHarness(), new PythonLanguageHarness(), new JavaScriptLanguageHarness()));

    @Test
    void acceptsAnOrdinarySignature() {
        assertThatCode(() -> validator.validate("twoSum", List.of("nums", "target", "max_value", "k2")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"two sum", "2sum", "_private", "two-sum", "café", ""})
    void rejectsNamesThatAreNotPortableIdentifiers(String name) {
        assertThatThrownBy(() -> validator.validate(name, List.of("x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_INVALID_IDENTIFIER))
                .hasMessageContaining("starting with a letter");
    }

    @Test
    void namesEveryLanguageThatReservesAName() {
        // `class` is a keyword in all three languages.
        assertThatThrownBy(() -> validator.validate("solve", List.of("class")))
                .hasMessage("'class' can't be used as a parameter name: it is reserved in JAVA, PYTHON, JAVASCRIPT");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "int",       // Java keyword only
            "def",       // Python keyword only
            "None",      // Python literal
            "function",  // JavaScript keyword only
            "self",      // Python harness: duplicates the method's own `self` argument
            "require",   // JavaScript harness: `var require` would shadow Node's require('fs')
            "JSON",      // JavaScript harness: the entry point calls JSON.parse/stringify
    })
    void rejectsKeywordsAndHarnessOwnedNamesOfAnySingleLanguage(String name) {
        assertThatThrownBy(() -> validator.validate("solve", List.of(name)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("it is reserved in");
    }

    @Test
    void reservedNamesAreCaseSensitiveLikeTheLanguages() {
        assertThatCode(() -> validator.validate("Class", List.of("Int", "none", "json")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAReservedMethodNameToo() {
        assertThatThrownBy(() -> validator.validate("console", List.of("x")))
                .hasMessage("'console' can't be used as a method name: it is reserved in JAVASCRIPT");
    }

    @Test
    void rejectsDuplicateParameterNames() {
        assertThatThrownBy(() -> validator.validate("solve", List.of("a", "b", "a")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_DUPLICATE_PARAMETER_NAME))
                .hasMessage("Parameter name 'a' is used more than once");
    }

    @Test
    void reservedNameFilesAreParsedWithoutCommentsOrBlankLines() {
        LanguageHarness java = new JavaLanguageHarness();
        assertThat(java.reservedNames()).contains("class", "record").doesNotContain("", "#");
        assertThat(java.reservedNames()).noneMatch(n -> n.contains(" ") || n.startsWith("#"));
    }
}
