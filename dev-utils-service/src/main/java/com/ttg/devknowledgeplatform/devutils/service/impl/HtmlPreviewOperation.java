package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Sanitizes raw HTML down to markup that is safe to render as a live preview in the GUI's own
 * sandboxed iframe — the first {@link OperationGroup#WEB} operation to actually declare that
 * group.
 *
 * <p>Genuinely different from {@link HtmlBeautifyOperation}'s own jsoup use, despite sharing the
 * same library: that operation only reformats whitespace/indentation, and its output is never
 * rendered as trusted HTML (see its own class Javadoc, and the {@code pom.xml} dependency comment
 * this class's addition updated). This operation's whole purpose is producing markup the GUI
 * actually renders, so it runs jsoup's {@link Cleaner} over a {@link Safelist} instead of just
 * reformatting: every {@code <script>} tag, every {@code on*} event-handler attribute, and every
 * non-http(s)/data/mailto URL (a {@code javascript:} href/src, in particular) is stripped, never
 * merely reformatted. This is deliberate defense in depth, not the only guard — the GUI's own
 * preview iframe is additionally sandboxed with no {@code allow-scripts} permission (per direct
 * request; see {@code gui/CLAUDE.md}'s dev-utils section), so a script tag surviving this
 * sanitizer somehow still could not execute — but a fully public, unauthenticated endpoint that
 * hands attacker-controlled markup back for a browser to render should not rely on the client
 * alone to keep that markup inert.
 *
 * <p>Built on {@link Safelist#relaxed()} (the same base most mainstream sanitizers start from —
 * headings/lists/tables/basic inline formatting/images/links) plus {@code style}/{@code class}/
 * {@code id} attributes on every element (so a preview can look like more than unstyled text) and
 * the {@code data:} protocol on {@code <img src>} (so a base64-embedded image — this module's own
 * "Base64 Image" GUI operation's natural companion — renders too). <b>Known, deliberate
 * limitation, not chased further</b>: a {@code <style>} block is stripped entirely, the same as
 * {@code <script>} — jsoup's {@link Cleaner} has no clean way to preserve a safelisted element's
 * raw (non-HTML-escaped) text content, which a {@code <style>} block's own CSS needs; the
 * per-element {@code style} attribute (already allowed) covers the common single-element-styling
 * case instead. Like {@link HtmlBeautifyOperation}, this never throws — jsoup's parser is
 * deliberately lenient and always produces a best-effort DOM, so there is no invalid-HTML failure
 * path here either (see {@code DevUtilsErrorCode}'s own Javadoc).
 */
@Component
public class HtmlPreviewOperation implements DevUtilOperation {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "style", "class", "id")
            .addProtocols("img", "src", "data");

    @Override
    public OperationGroup group() {
        return OperationGroup.WEB;
    }

    /**
     * Sanitizes {@code input} down to markup safe to hand to a (still-sandboxed) preview renderer
     * — see this class's own Javadoc for the exact {@link Safelist} and its known limitations.
     * Never throws.
     *
     * @param input raw, untrusted HTML to sanitize for rendering
     * @return the sanitized HTML
     */
    public String execute(String input) {
        Document dirty = Jsoup.parseBodyFragment(input);
        Document clean = new Cleaner(SAFELIST).clean(dirty);
        clean.outputSettings().prettyPrint(false);
        return clean.body().html();
    }
}
