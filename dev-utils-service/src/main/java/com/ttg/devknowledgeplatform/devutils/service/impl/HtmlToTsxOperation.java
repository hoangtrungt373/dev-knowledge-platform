package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.HtmlToTsxConverter;

/**
 * Converts raw HTML into JSX-flavored markup, branded "HTML to TSX" (per direct request — see
 * {@link HtmlToTsxConverter}'s own Javadoc for why "TSX" is a naming/file-extension choice, not a
 * different output shape: {@code class}→{@code className}, {@code style="..."}→a real object
 * literal, void elements force-closed with {@code />}, and so on) — a thin pass-through to
 * {@link HtmlToTsxConverter}, the same "operation class stays thin, the real logic lives in a
 * shared {@code service.impl.support} utility" shape {@code CssOperation}/{@code SqlFormatOperation}
 * already establish for their own delegate formatters. The third {@link OperationGroup#WEB}
 * operation, alongside {@code HtmlPreviewOperation}/{@code MarkdownPreviewOperation} — genuinely
 * different from both, though: this one is a plain text-to-text converter (like
 * {@code HtmlBeautifyOperation}), never rendered as live HTML anywhere, so it needs none of
 * {@code HtmlSanitizer}'s own script/event-handler stripping — an {@code onclick}/{@code <script>}
 * in the input is data to convert, not a live-rendering risk, since this operation's own output is
 * never handed to a browser to execute.
 *
 * <p>See {@link HtmlToTsxConverter}'s own Javadoc for the full attribute-renaming/style-object/
 * void-element/event-handler rules and every documented, deliberate limitation.
 */
@Component
public class HtmlToTsxOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.WEB;
    }

    /**
     * Converts {@code input} into JSX-flavored (TSX-branded) markup, pretty-printed or (with
     * {@code minify}) jsoup's own unformatted output mode — see {@link HtmlToTsxConverter}'s own
     * Javadoc for the full conversion rules. Never throws.
     *
     * @param input  raw HTML to convert
     * @param minify {@code false} for indented, {@code true} for jsoup's unformatted output
     * @return the converted markup
     */
    public String execute(String input, boolean minify) {
        return HtmlToTsxConverter.convert(input, minify);
    }
}
