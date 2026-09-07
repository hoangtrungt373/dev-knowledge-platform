package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;

/**
 * Reformats raw HTML with consistent indentation. jsoup's parser is deliberately lenient — it
 * never throws on malformed markup, it always produces a best-effort DOM — so unlike the JSON/YAML
 * operations, there is no invalid-HTML failure path here (see {@code DevUtilsErrorCode}'s Javadoc).
 *
 * <p>Input is parsed as a body fragment, not a full document, so a snippet in yields a snippet out
 * — a full {@code <html>} document's {@code <head>} is dropped in the process, the same trade-off
 * most standalone HTML-beautifier tools make, since this operation targets pasted markup snippets
 * rather than whole pages.
 */
@Component
public class HtmlBeautifyOperation implements DevUtilOperation {

    @Override
    public String execute(String input) {
        Document document = Jsoup.parseBodyFragment(input);
        document.outputSettings().prettyPrint(true).indentAmount(2);
        return document.body().html();
    }
}
