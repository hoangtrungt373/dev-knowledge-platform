package com.ttg.devknowledgeplatform.devpractice.editorial;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;

/**
 * Code placeholders in a solution's Markdown explanation — {@code {{code:JAVA}}} on a paragraph of its
 * own — so an explanation can show an approach's code in the flow of the text without anyone ever
 * typing (and then forgetting to update) a copy of it. Two halves, deliberately in one class so they
 * share one definition of what a placeholder is:
 *
 * <ul>
 *   <li>{@link #validate} at write time: no hand-written code blocks at all (fenced <em>or</em>
 *       indented), every placeholder a paragraph of its own, and naming a language the approach
 *       actually has an implementation in;</li>
 *   <li>{@link #render} at read time: each placeholder becomes a fenced block holding that language's
 *       <em>verified</em> code (the linked reference run's own source), or a short "being re-verified"
 *       note when the run is pending, rejected or stale — never outdated code.</li>
 * </ul>
 *
 * <p>Validation parses the text with commonmark-java rather than matching lines with a regex:
 * Markdown also treats a 4-space-indented line as code, and only a real parser can tell that apart
 * from a list item's continuation text. Rendering can then safely work line by line, because
 * validation guarantees every placeholder is alone on its line and no code block exists to contain
 * a look-alike. Placeholders are case-insensitive ({@code {{code:java}}} works); to show the literal
 * text, wrap it in an inline code span.
 */
public final class CodePlaceholders {

    private static final String MARKER = "{{code:";
    /** A placeholder by itself (validation checks a paragraph's text against this). */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{code:([A-Za-z]+)}}");
    /** A placeholder line, keeping its indentation (a placeholder inside a list item is indented). */
    private static final Pattern PLACEHOLDER_LINE = Pattern.compile("(?m)^([ \\t]*)\\{\\{code:([A-Za-z]+)}}[ \\t]*$");
    private static final Parser PARSER = Parser.builder().build();

    private CodePlaceholders() {
    }

    /**
     * Checks an explanation before it's stored.
     *
     * @param explanation  the Markdown as the author wrote it
     * @param implemented  languages the approach links a reference run in
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException {@code PROBLEM_SOLUTION_003}
     *         for a hand-written code block, {@code 005} for a placeholder that isn't a paragraph of its
     *         own, {@code 004} for a placeholder naming a language the approach has no implementation in
     */
    public static void validate(String explanation, Set<ProgrammingLanguage> implemented) {
        PARSER.parse(explanation).accept(new AbstractVisitor() {
            @Override
            public void visit(FencedCodeBlock codeBlock) {
                Validator.isTrue(false, DevPracticeErrorCode.PROBLEM_SOLUTION_RAW_CODE_BLOCK);
            }

            @Override
            public void visit(IndentedCodeBlock codeBlock) {
                Validator.isTrue(false, DevPracticeErrorCode.PROBLEM_SOLUTION_RAW_CODE_BLOCK);
            }

            @Override
            public void visit(Text text) {
                String literal = text.getLiteral();
                if (!literal.contains(MARKER)) {
                    return;
                }
                Matcher placeholder = PLACEHOLDER.matcher(literal.strip());
                boolean standalone = text.getParent() instanceof Paragraph
                        && text.getPrevious() == null && text.getNext() == null
                        && placeholder.matches();
                Validator.isTrue(standalone, DevPracticeErrorCode.PROBLEM_SOLUTION_PLACEHOLDER_MISPLACED,
                        (Object) literal.strip());
                String name = placeholder.group(1).toUpperCase(Locale.ROOT);
                ProgrammingLanguage language = languageOrNull(name);
                Validator.isTrue(language != null && implemented.contains(language),
                        DevPracticeErrorCode.PROBLEM_SOLUTION_PLACEHOLDER_WITHOUT_CODE, (Object) name);
            }
        });
    }

    /**
     * Expands every placeholder of an already-validated explanation.
     *
     * @param explanation  the stored Markdown
     * @param verifiedCode each language's verified source — exactly what the code panel shows
     * @return Markdown with each placeholder replaced by a fenced code block, or by an italic
     *         "being re-verified" note when that language has no verified code right now
     */
    public static String render(String explanation, Map<ProgrammingLanguage, String> verifiedCode) {
        Matcher line = PLACEHOLDER_LINE.matcher(explanation);
        StringBuilder out = new StringBuilder();
        while (line.find()) {
            String indent = line.group(1);
            ProgrammingLanguage language = languageOrNull(line.group(2).toUpperCase(Locale.ROOT));
            String code = language == null ? null : verifiedCode.get(language);
            String replacement = code == null
                    ? indent + "_The " + displayName(language, line.group(2)) + " code for this approach is being re-verified._"
                    : fenced(code, language, indent);
            line.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        line.appendTail(out);
        return out.toString();
    }

    /**
     * A fenced block for {@code code}, every line indented like the placeholder was. The fence is one
     * backtick longer than the longest backtick run in the code (minimum three), so code that itself
     * contains a triple backtick — a Markdown string literal, say — can't close the block early.
     */
    static String fenced(String code, ProgrammingLanguage language, String indent) {
        String body = code.stripTrailing();
        int longestRun = 0;
        int run = 0;
        for (int i = 0; i < body.length(); i++) {
            run = body.charAt(i) == '`' ? run + 1 : 0;
            longestRun = Math.max(longestRun, run);
        }
        String fence = "`".repeat(Math.max(3, longestRun + 1));
        String indentedBody = body.lines().map(l -> l.isEmpty() ? l : indent + l).collect(Collectors.joining("\n"));
        return indent + fence + language.name().toLowerCase(Locale.ROOT) + "\n" + indentedBody + "\n" + indent + fence;
    }

    private static ProgrammingLanguage languageOrNull(String upperCaseName) {
        try {
            return ProgrammingLanguage.valueOf(upperCaseName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String displayName(ProgrammingLanguage language, String fallback) {
        if (language == null) {
            return fallback;
        }
        return switch (language) {
            case JAVA -> "Java";
            case PYTHON -> "Python";
            case JAVASCRIPT -> "JavaScript";
        };
    }
}
