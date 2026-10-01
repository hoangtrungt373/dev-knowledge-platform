package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Reads a Python template such as
 * <pre>{@code
 * class Solution:
 *     def evalRPN(self, tokens: List[str]) -> int:
 * }</pre>
 * Every parameter and the return value need a type hint — untyped Python can't say whether
 * {@code nums} is an {@code int[]} or a {@code String[]}. A leading {@code self} is dropped.
 * Accepts {@code List[int]}, PEP 585's {@code list[int]} and {@code typing.List[int]} alike.
 *
 * <p>No full Python parser exists for the JVM worth the dependency for this; a signature line has a
 * small, regular grammar, and {@link TemplateParsing#splitTopLevel} handles the one tricky part
 * (commas inside {@code List[...]}). Python's {@code int} covers both {@code INT} and {@code LONG},
 * so it comes back with {@code LONG} as an alternative — see {@link ParsedSignature.TypeChoice}.
 */
@Component
public class PythonSignatureTemplateParser implements SignatureTemplateParser {

    // `def name(params) -> ret:` — params can't contain ')' (type hints only use brackets), and the
    // return annotation runs up to the first ':' (none of the supported types contain one).
    private static final Pattern DEF = Pattern.compile(
            "def\\s+([A-Za-z_]\\w*)\\s*\\((.*?)\\)\\s*(?:->\\s*([^:]+?))?\\s*:", Pattern.DOTALL);

    private final TypeDeclarationIndex types =
            new TypeDeclarationIndex(new PythonTypeRenderer(), PythonSignatureTemplateParser::normalize);

    @Override
    public ProgrammingLanguage language() {
        return ProgrammingLanguage.PYTHON;
    }

    @Override
    public ParsedSignature parse(String code) {
        Matcher matcher = DEF.matcher(stripComments(code));
        List<String[]> defs = new ArrayList<>();
        while (matcher.find()) {
            defs.add(new String[] {matcher.group(1), matcher.group(2), matcher.group(3)});
        }
        if (defs.isEmpty()) {
            throw TemplateParsing.invalid("no `def` found — expected e.g. "
                    + "`def evalRPN(self, tokens: List[str]) -> int:`");
        }
        if (defs.size() > 1) {
            throw TemplateParsing.invalid("found " + defs.size() + " functions ("
                    + String.join(", ", defs.stream().map(d -> d[0]).toList())
                    + ") — a template declares only the one method users implement");
        }

        String[] def = defs.get(0);
        if (def[2] == null) {
            throw TemplateParsing.invalid("'" + def[0] + "' has no return type hint — add one, e.g. `-> int`");
        }
        List<String> rawParams = new ArrayList<>(TemplateParsing.splitTopLevel(def[1]));
        if (!rawParams.isEmpty() && rawParams.get(0).equals("self")) {
            rawParams.remove(0);
        }
        List<ParsedSignature.ParsedParameter> parameters = rawParams.stream().map(this::toParameter).toList();
        return new ParsedSignature(def[0], types.resolve(def[2], "return type"), parameters);
    }

    private ParsedSignature.ParsedParameter toParameter(String raw) {
        if (raw.startsWith("*")) {
            throw TemplateParsing.invalid("'" + raw + "' — *args/**kwargs aren't supported, use a List parameter");
        }
        int colon = raw.indexOf(':');
        String name = (colon < 0 ? raw : raw.substring(0, colon)).strip();
        if (colon < 0) {
            throw TemplateParsing.invalid("parameter '" + name + "' has no type hint — write e.g. `" + name + ": int`");
        }
        String type = raw.substring(colon + 1);
        if (type.contains("=")) {
            throw TemplateParsing.invalid("parameter '" + name + "' has a default value — remove it");
        }
        return new ParsedSignature.ParsedParameter(name, types.resolve(type, "parameter '" + name + "'"));
    }

    /** Whitespace-insensitive; {@code typing.} prefix and PEP 585 lowercase {@code list} accepted. */
    private static String normalize(String spelling) {
        return spelling.replaceAll("\\s+", "").replace("typing.", "").replaceAll("\\blist\\[", "List[");
    }

    /** Drops {@code #} comments so a commented-out {@code def} isn't mistaken for the real one. */
    private static String stripComments(String code) {
        return code.lines().map(line -> {
            int hash = line.indexOf('#');
            return hash >= 0 ? line.substring(0, hash) : line;
        }).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
