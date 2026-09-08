package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.CurlyBraceFormatter;

/**
 * Reformats raw CSS with consistent indentation, or, with {@code minify}, a compact form with
 * comments and non-essential whitespace stripped.
 *
 * <p>Delegates entirely to the shared {@link CurlyBraceFormatter} — see that class's own Javadoc
 * for why this is a lenient, brace/semicolon-driven reformatter rather than a full CSS-grammar
 * parser. Like {@code HtmlBeautifyOperation}, this never throws — there is no invalid-CSS failure
 * path here, so no matching {@code DevUtilsErrorCode} exists for this operation.
 */
@Component
public class CssOperation implements DevUtilOperation {

    public String execute(String input, boolean minify) {
        return minify ? CurlyBraceFormatter.minify(input) : CurlyBraceFormatter.beautify(input);
    }
}
