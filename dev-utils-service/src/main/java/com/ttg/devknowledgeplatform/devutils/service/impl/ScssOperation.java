package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.CurlyBraceFormatter;

/**
 * Reformats raw SCSS with consistent indentation, or, with {@code minify}, a compact form with
 * comments and non-essential whitespace stripped.
 *
 * <p><b>This only reformats — it does not compile SCSS to plain CSS.</b> SCSS's own extensions
 * ({@code $variables}, nesting, {@code &} parent selectors, {@code @mixin}/{@code @include}) aren't
 * resolved or validated here; they pass through as literal text, exactly as written, the same way
 * {@code CurlyBraceFormatter} treats any other content between braces/semicolons — same reasoning
 * as {@code LessOperation}'s own Javadoc. Like {@code HtmlBeautifyOperation}, this never throws —
 * no invalid-input failure path exists here, so no matching {@code DevUtilsErrorCode} exists for
 * this operation.
 */
@Component
public class ScssOperation implements DevUtilOperation {

    public String execute(String input, boolean minify) {
        return minify ? CurlyBraceFormatter.minify(input) : CurlyBraceFormatter.beautify(input);
    }
}
