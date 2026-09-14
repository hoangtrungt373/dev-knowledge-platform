package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.HtmlSanitizer;

/**
 * Sanitizes raw HTML down to markup that is safe to render as a live preview in the GUI's own
 * sandboxed iframe — the first {@link OperationGroup#WEB} operation to actually declare that
 * group.
 *
 * <p>Genuinely different from {@link HtmlBeautifyOperation}'s own jsoup use, despite sharing the
 * same library: that operation only reformats whitespace/indentation, and its output is never
 * rendered as trusted HTML (see its own class Javadoc, and the {@code pom.xml} dependency comment
 * this class's addition updated). This operation's whole purpose is producing markup the GUI
 * actually renders, so it delegates to {@link HtmlSanitizer} (jsoup's own {@code Cleaner} over a
 * {@code Safelist}) instead of just reformatting: every {@code <script>} tag, every {@code on*}
 * event-handler attribute, and every non-http(s)/data/mailto URL (a {@code javascript:} href/src,
 * in particular) is stripped, never merely reformatted — see {@link HtmlSanitizer}'s own Javadoc
 * for the exact Safelist and its known limitations. This is deliberate defense in depth, not the
 * only guard — the GUI's own preview iframe is additionally sandboxed with no
 * {@code allow-scripts} permission (per direct request; see {@code gui/CLAUDE.md}'s dev-utils
 * section), so a script tag surviving this sanitizer somehow still could not execute — but a fully
 * public, unauthenticated endpoint that hands attacker-controlled markup back for a browser to
 * render should not rely on the client alone to keep that markup inert.
 *
 * <p>{@link HtmlSanitizer} itself was extracted out of this class's own original inline
 * {@code Safelist} once {@code MarkdownPreviewOperation} needed the identical sanitization pass
 * over the HTML its own Markdown-to-HTML conversion produces — this class is now a thin
 * pass-through. Like {@link HtmlBeautifyOperation}, this never throws — jsoup's parser is
 * deliberately lenient and always produces a best-effort DOM, so there is no invalid-HTML failure
 * path here either (see {@code DevUtilsErrorCode}'s own Javadoc).
 */
@Component
public class HtmlPreviewOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.WEB;
    }

    /**
     * Sanitizes {@code input} down to markup safe to hand to a (still-sandboxed) preview renderer
     * — see {@link HtmlSanitizer}'s own Javadoc for the exact {@code Safelist} and its known
     * limitations. Never throws.
     *
     * @param input raw, untrusted HTML to sanitize for rendering
     * @return the sanitized HTML
     */
    public String execute(String input) {
        return HtmlSanitizer.sanitize(input);
    }
}
