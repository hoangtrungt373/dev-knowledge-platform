package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.CurlyBraceFormatter;

/**
 * Reformats raw LESS with consistent indentation, or, with {@code minify}, a compact form with
 * comments and non-essential whitespace stripped.
 *
 * <p><b>This only reformats — it does not compile LESS to plain CSS.</b> LESS's own extensions
 * (variables like {@code @width}, nesting, {@code &} parent selectors, mixins) aren't resolved or
 * validated here; they pass through as literal text, exactly as written, the same way
 * {@code CurlyBraceFormatter} treats any other content between braces/semicolons. A real
 * LESS-to-CSS compiler is a fundamentally different (and much larger) tool than a text reformatter
 * — see that class's own Javadoc for the full reasoning behind this module's brace-based approach.
 * Like {@code HtmlBeautifyOperation}, this never throws — no invalid-input failure path exists
 * here, so no matching {@code DevUtilsErrorCode} exists for this operation.
 */
@Component
public class LessOperation implements DevUtilOperation {

    /** Never throws — see this class's own Javadoc for why {@link CurlyBraceFormatter} has no
     * invalid-input failure path. */
    public String execute(String input, boolean minify) {
        return minify ? CurlyBraceFormatter.minify(input) : CurlyBraceFormatter.beautify(input);
    }
}
