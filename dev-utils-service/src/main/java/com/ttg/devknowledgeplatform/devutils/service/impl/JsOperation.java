package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.CurlyBraceFormatter;

/**
 * Reformats raw JavaScript with consistent indentation, or, with {@code minify}, a compact form
 * with comments and non-essential whitespace stripped.
 *
 * <p>Delegates entirely to the shared {@link CurlyBraceFormatter} — see that class's own Javadoc
 * for the full reasoning, in particular the <b>Automatic Semicolon Insertion (ASI) safety note</b>:
 * this is a textual reformatter, not a real JS parser, so it deliberately never collapses a line
 * break between two ordinary tokens (e.g. {@code return\nx}) into a space or nothing — doing so
 * could silently change what the code actually does. The practical cost is that {@code minify}
 * does not guarantee single-line output for JS the way it mostly does for CSS/LESS/SCSS. Like
 * {@code HtmlBeautifyOperation}, this never throws — no invalid-input failure path exists here, so
 * no matching {@code DevUtilsErrorCode} exists for this operation.
 */
@Component
public class JsOperation implements DevUtilOperation {

    public String execute(String input, boolean minify) {
        return minify ? CurlyBraceFormatter.minify(input) : CurlyBraceFormatter.beautify(input);
    }
}
