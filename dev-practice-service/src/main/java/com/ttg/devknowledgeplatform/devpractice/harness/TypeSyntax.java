package com.ttg.devknowledgeplatform.devpractice.harness;

/**
 * How one {@code ParamType} is spelled in one language — the per-type fragments a harness's
 * Mustache templates splice in (e.g. {@code {{type.declaration}}}).
 *
 * @param declaration   the type as written in a signature ({@code int[]}, {@code List[int]},
 *                      {@code number[]})
 * @param jsonConverter the name of the generated program's own JSON-to-native converter for this
 *                      type (Java's {@code JsonMini.toIntArray}), or {@code null} for a language
 *                      whose standard JSON parser already yields native values
 */
public record TypeSyntax(String declaration, String jsonConverter) {

    /** For languages (Python, JavaScript) whose stdlib JSON parser needs no per-type conversion. */
    static TypeSyntax declarationOnly(String declaration) {
        return new TypeSyntax(declaration, null);
    }
}
