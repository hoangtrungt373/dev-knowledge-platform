package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;

/**
 * Reformats raw HTML with consistent indentation, or, with {@code minify}, jsoup's own
 * unformatted output mode (no added indentation/line breaks between tags). jsoup's parser is
 * deliberately lenient — it never throws on malformed markup, it always produces a best-effort DOM
 * — so unlike the JSON/YAML operations, there is no invalid-HTML failure path here (see
 * {@code DevUtilsErrorCode}'s Javadoc).
 *
 * <p>Input is parsed as a body fragment, not a full document, so a snippet in yields a snippet out
 * — a full {@code <html>} document's {@code <head>} is dropped in the process, the same trade-off
 * most standalone HTML-beautifier tools make, since this operation targets pasted markup snippets
 * rather than whole pages.
 *
 * <p><b>{@code minify} is jsoup's {@code prettyPrint(false)} mode, not a true single-line
 * guarantee</b> — it drops jsoup's own added indentation/line breaks, but whitespace already
 * present inside a text node in the source is preserved as-is, so a source with its own embedded
 * newlines can still produce more than one output line. This is the standard, content-safe way
 * jsoup itself distinguishes "formatted" from "unformatted" output; genuinely collapsing all
 * whitespace would risk altering visible spacing in inline/whitespace-sensitive markup.
 */
@Component
public class HtmlBeautifyOperation implements DevUtilOperation {

    /** Never throws — see this class's own Javadoc for why jsoup's lenient parser has no
     * invalid-input failure path. */
    public String execute(String input, boolean minify) {
        Document document = Jsoup.parseBodyFragment(input);
        document.outputSettings().prettyPrint(!minify).indentAmount(2);
        return document.body().html();
    }
}
