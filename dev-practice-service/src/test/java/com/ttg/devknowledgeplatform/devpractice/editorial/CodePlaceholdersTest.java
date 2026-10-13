package com.ttg.devknowledgeplatform.devpractice.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;

/** Placeholders: what an explanation may contain, and what a learner reads once they're expanded. */
class CodePlaceholdersTest {

    private static final Set<ProgrammingLanguage> JAVA_ONLY = EnumSet.of(ProgrammingLanguage.JAVA);

    @Nested
    class Validate {

        @Test
        void acceptsProseInlineCodeAndAPlaceholderParagraph() {
            String explanation = """
                    Walk the array once; `Set.add` returns `false` for a repeat.

                    {{code:JAVA}}

                    1. Push numbers.
                    2. Pop on operators.

                       A continuation paragraph inside the list item is not code.

                    **Complexity** — `O(n)`.
                    """;

            assertThatCode(() -> CodePlaceholders.validate(explanation, JAVA_ONLY)).doesNotThrowAnyException();
        }

        @Test
        void placeholdersAreCaseInsensitive() {
            assertThatCode(() -> CodePlaceholders.validate("{{code:java}}", JAVA_ONLY)).doesNotThrowAnyException();
        }

        @Test
        void literalPlaceholderTextInsideInlineCodeIsJustText() {
            assertThatCode(() -> CodePlaceholders.validate("Write `{{code:PYTHON}}` to embed code.", JAVA_ONLY))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsAFencedCodeBlock() {
            String explanation = """
                    The key part:

                    ```java
                    for (int num : nums) { }
                    ```
                    """;

            assertRejected(explanation, DevPracticeErrorCode.PROBLEM_SOLUTION_RAW_CODE_BLOCK);
        }

        @Test
        void rejectsAnIndentedCodeBlock() {
            String explanation = "The key part:\n\n    for (int num : nums) { }\n";

            assertRejected(explanation, DevPracticeErrorCode.PROBLEM_SOLUTION_RAW_CODE_BLOCK);
        }

        @Test
        void rejectsAPlaceholderInsideASentence() {
            assertRejected("Here it is: {{code:JAVA}} — done.", DevPracticeErrorCode.PROBLEM_SOLUTION_PLACEHOLDER_MISPLACED);
        }

        @Test
        void rejectsAPlaceholderGluedToTheLineAbove() {
            // No blank line: Markdown joins the two lines into one paragraph.
            assertRejected("The code:\n{{code:JAVA}}", DevPracticeErrorCode.PROBLEM_SOLUTION_PLACEHOLDER_MISPLACED);
        }

        @Test
        void rejectsALanguageTheApproachHasNoImplementationIn() {
            assertRejected("{{code:PYTHON}}", DevPracticeErrorCode.PROBLEM_SOLUTION_PLACEHOLDER_WITHOUT_CODE);
        }

        @Test
        void rejectsAnUnknownLanguage() {
            assertRejected("{{code:RUBY}}", DevPracticeErrorCode.PROBLEM_SOLUTION_PLACEHOLDER_WITHOUT_CODE);
        }

        @Test
        void messagesShowTheLiteralPlaceholderSyntax() {
            // MessageFormat quoting: '{{' and '}}' must come out as literal braces.
            assertThatThrownBy(() -> CodePlaceholders.validate("```\nx\n```", JAVA_ONLY))
                    .hasMessageContaining("{{code:JAVA}}")
                    .hasMessageContaining("can't");
            assertThatThrownBy(() -> CodePlaceholders.validate("{{code:PYTHON}}", JAVA_ONLY))
                    .hasMessage("The explanation shows PYTHON code, but this approach has no PYTHON implementation");
        }

        private void assertRejected(String explanation, DevPracticeErrorCode expected) {
            assertThatThrownBy(() -> CodePlaceholders.validate(explanation, JAVA_ONLY))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        }
    }

    @Nested
    class Render {

        private static final String CODE = "class Solution {\n    int x;\n}\n";

        @Test
        void replacesAPlaceholderWithTheVerifiedCodeAsAFencedBlock() {
            String rendered = CodePlaceholders.render("Intro.\n\n{{code:JAVA}}\n\nOutro.",
                    Map.of(ProgrammingLanguage.JAVA, CODE));

            assertThat(rendered).isEqualTo("Intro.\n\n```java\nclass Solution {\n    int x;\n}\n```\n\nOutro.");
        }

        @Test
        void aLanguageWithoutVerifiedCodeShowsANoteInsteadOfStaleCode() {
            String rendered = CodePlaceholders.render("{{code:JAVA}}", Map.of());

            assertThat(rendered).isEqualTo("_The Java code for this approach is being re-verified._");
        }

        @Test
        void keepsAListItemsIndentationOnEveryLine() {
            String rendered = CodePlaceholders.render("1. Step\n\n   {{code:java}}", Map.of(ProgrammingLanguage.JAVA, CODE));

            assertThat(rendered).isEqualTo("1. Step\n\n   ```java\n   class Solution {\n       int x;\n   }\n   ```");
        }

        @Test
        void usesALongerFenceWhenTheCodeItselfContainsBackticks() {
            String code = "String md = \"```\";";

            String rendered = CodePlaceholders.render("{{code:JAVA}}", Map.of(ProgrammingLanguage.JAVA, code));

            assertThat(rendered).isEqualTo("````java\nString md = \"```\";\n````");
        }

        @Test
        void leavesTextWithoutPlaceholdersUntouched() {
            String text = "Plain `{{code:JAVA}}` mention and **bold**.";

            assertThat(CodePlaceholders.render(text, Map.of(ProgrammingLanguage.JAVA, CODE))).isEqualTo(text);
        }
    }
}
