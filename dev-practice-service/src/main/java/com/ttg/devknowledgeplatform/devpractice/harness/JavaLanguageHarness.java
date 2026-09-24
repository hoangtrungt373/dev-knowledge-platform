package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * The one harness that needs a hand-rolled JSON helper — Judge0's Java runtime has no application
 * classpath, so this module's own Jackson dependency is never available inside a submission's
 * sandboxed run, unlike Python's {@code json}/JavaScript's {@code JSON} (both stdlib/native).
 * {@code harness/java/JsonMini.java} is embedded verbatim into every generated program via the
 * prelude template's {@code {{includes.jsonMini}}}; it only supports the closed {@code ParamType}
 * vocabulary (numbers, strings, booleans, arrays, up to one level of nesting for a matrix) and is
 * deliberately not a general-purpose JSON parser.
 *
 * <p>Judge0's Java language expects a single {@code public class Main} per submission with every
 * other top-level type left package-private in the same file — the user's own {@code class
 * Solution} (no {@code public} modifier, matching the starter code) already fits that, so the
 * generated program is exactly: {@code JsonMini} + the user's {@code Solution} + a generated
 * {@code public class Main}.
 */
@Component
public class JavaLanguageHarness extends LanguageHarness {

    public JavaLanguageHarness() {
        super(ProgrammingLanguage.JAVA, new JavaTypeRenderer(), Map.of("jsonMini", "JsonMini.java"));
    }
}
