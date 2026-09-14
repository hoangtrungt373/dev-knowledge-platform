package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.HtmlSanitizer;

/**
 * Converts raw Markdown to HTML (commonmark-java, GFM tables/strikethrough/task-list-items) and
 * sanitizes the result via the shared {@link HtmlSanitizer} — the second {@link OperationGroup#WEB}
 * operation, and {@link HtmlPreviewOperation}'s direct sibling: both feed the GUI's identical
 * sandboxed, script-free preview iframe, just from a different source format.
 *
 * <p><b>Why sanitize at all, given commonmark-java's own output is never trusted-then-blindly-
 * rendered elsewhere in this reactor</b> — {@code gui}'s own Markdown renderers
 * (`@chat/components/MarkdownRenderer.tsx`, `@content/components/MarkdownField.tsx`) use
 * `react-markdown`, which renders to React elements directly and never calls
 * `dangerouslySetInnerHTML`, so raw HTML embedded in a Markdown document is escaped there, not
 * executed, with no sanitizer needed on that side. This operation is different: standard Markdown
 * syntax allows literal inline HTML to pass through a renderer untouched (CommonMark's own
 * spec — an author can write `<script>` or `<img onerror=...>` directly inside a `.md` document,
 * which `HtmlRenderer` faithfully reproduces in its output by design), and this operation's own
 * output is handed straight to the GUI's `srcdoc` iframe as real HTML, not escaped React text — so
 * without a sanitization pass, an embedded `<script>` in the Markdown input would reach the preview
 * frame as a live script tag. Running the same {@link HtmlSanitizer} {@link HtmlPreviewOperation}
 * already uses closes that gap, and keeps both Web-group preview operations sharing one sanitizer
 * rather than each inventing its own.
 *
 * <p><b>Known, deliberate scope trim: no GFM autolink extension</b> (bare {@code www.}/{@code http}
 * URLs auto-linked with no angle brackets) — commonmark-java's own autolink extension pulls in a
 * further runtime dependency this module doesn't otherwise need, and CommonMark's own core syntax
 * already autolinks a bracketed {@code <https://...>} form without it; not a correctness gap, just
 * a narrower autolinking convenience than {@code gui}'s own {@code remark-gfm} plugin offers.
 *
 * <p>Never throws — commonmark-java's own parser, like jsoup's, is deliberately lenient and always
 * produces a best-effort document for any input text (there is no such thing as syntactically
 * invalid Markdown), so there is no invalid-input failure path here either (see
 * {@code DevUtilsErrorCode}'s own Javadoc).
 */
@Component
public class MarkdownPreviewOperation implements DevUtilOperation {

    private static final List<Extension> EXTENSIONS =
            List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();

    @Override
    public OperationGroup group() {
        return OperationGroup.WEB;
    }

    /**
     * Converts {@code input} (Markdown, with GFM tables/strikethrough/task-list-items support)
     * into sanitized HTML safe to hand to a (still-sandboxed) preview renderer — see this class's
     * own Javadoc for why sanitization is needed here even though {@link HtmlPreviewOperation}'s
     * own input is already raw HTML. Never throws.
     *
     * @param input raw Markdown to render as a preview
     * @return the sanitized HTML rendering of {@code input}
     */
    public String execute(String input) {
        Node document = PARSER.parse(input);
        String html = RENDERER.render(document);
        return HtmlSanitizer.sanitize(html);
    }
}
