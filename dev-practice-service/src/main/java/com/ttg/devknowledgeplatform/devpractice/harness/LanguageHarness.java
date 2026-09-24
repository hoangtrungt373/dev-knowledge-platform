package com.ttg.devknowledgeplatform.devpractice.harness;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Turns a submission's method body into a full, standalone program Judge0 can compile and run —
 * one subclass per {@link ProgrammingLanguage}, selected by {@link LanguageHarnessRegistry}.
 *
 * <p>{@link #buildProgram} is the fixed skeleton every language shares — prelude, then the user's
 * own code verbatim, then a generated entry point that reads a {@code TestCase.input} JSON
 * argument array from stdin, calls the user's method, and prints the JSON-encoded result. What
 * varies per language is split across two collaborators, both supplied by the subclass:
 * <ul>
 *   <li><b>Program layout</b> — three Mustache templates under {@code harness/{language}/}
 *       ({@code prelude}, {@code main}, {@code starter}), so a language's generated code reads as
 *       ordinary source rather than Java string-building.</li>
 *   <li><b>Per-type syntax</b> — a {@link TypeRenderer} (Strategy), which maps every
 *       {@code ParamType} to its spelling via an exhaustive switch, keeping the compile-time
 *       guarantee that every harness supports every type.</li>
 * </ul>
 *
 * <p><b>User code is never passed through Mustache</b> — it is concatenated between the rendered
 * prelude and entry point, so a submission containing {@code {{...}}} is never interpreted as a
 * template tag. Templates are compiled once at construction, so a missing or malformed template
 * fails application startup rather than a submission.
 *
 * <p>Template context (every template sees the same one): {@code methodName}; {@code returnType}
 * and each {@code params[].type} as a {@link TypeSyntax}; {@code params[].name}/{@code position};
 * and {@code includes.*} — verbatim resource files a subclass declares (Java's {@code JsonMini}).
 */
public abstract class LanguageHarness {

    /** HTML escaping off: templates emit source code, where {@code <}/{@code &} must survive as-is. */
    private static final Mustache.Compiler COMPILER = Mustache.compiler().escapeHTML(false);

    private final ProgrammingLanguage language;
    private final TypeRenderer typeRenderer;
    private final Map<String, String> includes;
    private final Template prelude;
    private final Template main;
    private final Template starter;

    /**
     * @param language     the language this harness generates programs for; also names its
     *                     template directory ({@code harness/java/}, ...)
     * @param typeRenderer this language's per-{@code ParamType} syntax
     * @param includes     template-context key → file name (in the same template directory) of a
     *                     resource inserted verbatim via {@code {{includes.<key>}}}, without its
     *                     trailing newline
     */
    protected LanguageHarness(ProgrammingLanguage language, TypeRenderer typeRenderer, Map<String, String> includes) {
        this.language = language;
        this.typeRenderer = typeRenderer;
        Map<String, String> loaded = new LinkedHashMap<>();
        includes.forEach((key, fileName) -> loaded.put(key, readResource(fileName).stripTrailing()));
        this.includes = Map.copyOf(loaded);
        this.prelude = compile("prelude");
        this.main = compile("main");
        this.starter = compile("starter");
    }

    /** The language this harness generates programs for. */
    public final ProgrammingLanguage language() {
        return language;
    }

    /**
     * Builds the full program Judge0 should compile/run. The result depends only on the problem's
     * signature, not on any test case — each test case's input arrives on stdin, so one program
     * is built per submission and reused across all of its test cases.
     *
     * @param problem  the problem being submitted against (supplies the method signature)
     * @param userCode the submission's own source (a method/class body, never a full program)
     * @return the complete program text
     */
    public final String buildProgram(Problem problem, String userCode) {
        Map<String, Object> context = context(problem);
        return prelude.execute(context) + userCode.strip() + "\n\n" + main.execute(context);
    }

    /**
     * The stub shown to a user before they've written anything — the method signature with an
     * empty body, in this harness's own language syntax.
     *
     * @param problem the problem to render a stub for
     * @return starter source code
     */
    public final String renderStarterCode(Problem problem) {
        return starter.execute(context(problem));
    }

    /**
     * One signature parameter as templates see it.
     *
     * @param name     the parameter's name
     * @param type     its spelling in this harness's language
     * @param position its 0-based index in both the signature and the stdin argument array
     */
    public record ParameterView(String name, TypeSyntax type, int position) {
    }

    private Map<String, Object> context(Problem problem) {
        List<ParameterView> params = problem.getParameters().stream()
                .sorted(Comparator.comparing(MethodParameter::getPosition))
                .map(p -> new ParameterView(p.getName(), typeRenderer.render(p.getType()), p.getPosition()))
                .toList();
        return Map.of(
                "methodName", problem.getMethodName(),
                "returnType", typeRenderer.render(problem.getReturnType()),
                "params", params,
                "includes", includes);
    }

    private Template compile(String name) {
        return COMPILER.compile(readResource(name + ".mustache"));
    }

    private String readResource(String fileName) {
        String path = "harness/" + language.name().toLowerCase(Locale.ROOT) + "/" + fileName;
        try (InputStream in = LanguageHarness.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing harness resource on classpath: " + path);
            }
            // Normalizes CRLF defensively — generated programs must be identical on every checkout.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read harness resource " + path, e);
        }
    }
}
