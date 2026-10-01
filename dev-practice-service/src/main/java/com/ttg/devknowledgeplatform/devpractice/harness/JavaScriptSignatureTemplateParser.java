package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Reads a JavaScript template in LeetCode's own shape:
 * <pre>{@code
 * /**
 *  * @param {string[]} tokens
 *  * @return {number}
 *  *\/
 * var evalRPN = function(tokens) { };
 * }</pre>
 * JavaScript signatures carry no types, so they come from the JSDoc {@code @param}/{@code @return}
 * tags — every parameter needs one. Also accepts {@code function evalRPN(tokens)} and
 * {@code const evalRPN = (tokens) => ...}, and {@code Array<number>} for {@code number[]}.
 *
 * <p>JavaScript has one {@code number} type, so {@code number} could be {@code INT}, {@code LONG}
 * or {@code DOUBLE} and {@code number[]} {@code INT_ARRAY} or {@code DOUBLE_ARRAY}; those come back
 * with alternatives for the admin to confirm (see {@link ParsedSignature.TypeChoice}).
 */
@Component
public class JavaScriptSignatureTemplateParser implements SignatureTemplateParser {

    private static final List<Pattern> FUNCTION_FORMS = List.of(
            Pattern.compile("(?:var|let|const)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*function\\s*\\(([^)]*)\\)"),
            Pattern.compile("function\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)"),
            Pattern.compile("(?:var|let|const)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*\\(([^)]*)\\)\\s*=>"));
    private static final Pattern JSDOC = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL);
    // `@param {type} name` — also `[name]` (optional) and `name - description`.
    private static final Pattern PARAM_TAG = Pattern.compile("@param\\s+\\{([^}]*)}\\s+\\[?([A-Za-z_$][\\w$]*)");
    private static final Pattern RETURN_TAG = Pattern.compile("@returns?\\s+\\{([^}]*)}");
    private static final Pattern ARRAY_GENERIC = Pattern.compile("Array<([^<>]+)>");

    private final TypeDeclarationIndex types =
            new TypeDeclarationIndex(new JavaScriptTypeRenderer(), JavaScriptSignatureTemplateParser::normalize);

    @Override
    public ProgrammingLanguage language() {
        return ProgrammingLanguage.JAVASCRIPT;
    }

    @Override
    public ParsedSignature parse(String code) {
        Matcher function = firstFunction(code);
        String name = function.group(1);
        List<String> paramNames = TemplateParsing.splitTopLevel(function.group(2));
        paramNames.stream().filter(p -> !p.matches("[A-Za-z_$][\\w$]*")).findFirst().ifPresent(p -> {
            throw TemplateParsing.invalid("parameter '" + p + "' — defaults/destructuring/rest aren't supported");
        });

        String doc = jsDocBefore(code, function.start(), name);
        Map<String, String> paramTypes = new HashMap<>();
        Matcher tag = PARAM_TAG.matcher(doc);
        while (tag.find()) {
            paramTypes.put(tag.group(2), tag.group(1));
        }
        Matcher returnTag = RETURN_TAG.matcher(doc);
        if (!returnTag.find()) {
            throw TemplateParsing.invalid("'" + name + "' has no `@return {type}` JSDoc tag — add e.g. `@return {number}`");
        }

        List<ParsedSignature.ParsedParameter> parameters = paramNames.stream().map(param -> {
            String type = paramTypes.get(param);
            if (type == null) {
                throw TemplateParsing.invalid("parameter '" + param + "' has no `@param {type} " + param
                        + "` JSDoc tag — JavaScript signatures need one to know the type");
            }
            return new ParsedSignature.ParsedParameter(param, types.resolve(type, "parameter '" + param + "'"));
        }).toList();
        return new ParsedSignature(name, types.resolve(returnTag.group(1), "return type"), parameters);
    }

    /** The earliest function declaration in the template, in any of the accepted forms. */
    private static Matcher firstFunction(String code) {
        Matcher best = null;
        for (Pattern form : FUNCTION_FORMS) {
            Matcher m = form.matcher(code);
            if (m.find() && (best == null || m.start() < best.start())) {
                best = m;
            }
        }
        if (best == null) {
            throw TemplateParsing.invalid("no function found — expected e.g. `var evalRPN = function(tokens) { };`");
        }
        return best;
    }

    /** The last JSDoc block that ends before the function — the one documenting it. */
    private static String jsDocBefore(String code, int functionStart, String name) {
        Matcher m = JSDOC.matcher(code);
        String doc = null;
        while (m.find() && m.end() <= functionStart) {
            doc = m.group(1);
        }
        if (doc == null) {
            throw TemplateParsing.invalid("'" + name + "' has no JSDoc comment — add `/** @param {type} name ... @return {type} */` above it");
        }
        return doc;
    }

    /** Whitespace-insensitive; {@code Array<T>} → {@code T[]}; boxed names lower-cased. */
    private static String normalize(String spelling) {
        String s = spelling.replaceAll("\\s+", "")
                .replace("Number", "number").replace("String", "string").replace("Boolean", "boolean");
        Matcher m = ARRAY_GENERIC.matcher(s);
        while (m.find()) {
            s = m.replaceFirst(Matcher.quoteReplacement(m.group(1) + "[]"));
            m = ARRAY_GENERIC.matcher(s);
        }
        return s;
    }
}
