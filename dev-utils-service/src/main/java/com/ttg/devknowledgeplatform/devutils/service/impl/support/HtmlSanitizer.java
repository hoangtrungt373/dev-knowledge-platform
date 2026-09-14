package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * Sanitizes raw (untrusted) HTML down to markup safe to render — extracted out of
 * {@code HtmlPreviewOperation}'s own original inline {@code Safelist} once
 * {@code MarkdownPreviewOperation} needed the identical sanitization pass over the HTML its own
 * Markdown-to-HTML conversion produces, rather than a second, drifting copy of the same
 * {@link Safelist}. Static, stateless utility — not itself a {@code DevUtilOperation}, the same
 * "shared support, not a page-level operation" role {@code CurlyBraceFormatter}/{@code SqlFormatter}
 * already play for the Formatters group.
 *
 * <p>Built on {@link Safelist#relaxed()} (the same base most mainstream sanitizers start from —
 * headings/lists/tables/basic inline formatting/images/links) plus {@code style}/{@code class}/
 * {@code id} attributes on every element (so a preview can look like more than unstyled text) and
 * the {@code data:} protocol on {@code <img src>} (so a base64-embedded image — this module's own
 * "Base64 Image" GUI operation's natural companion — renders too). Every {@code <script>} tag,
 * every {@code on*} event-handler attribute, and every non-http(s)/data/mailto URL (a
 * {@code javascript:} href/src, in particular) is stripped. <b>Known, deliberate limitation, not
 * chased further</b>: a {@code <style>} block is stripped entirely, the same as {@code <script>}
 * — jsoup's {@link Cleaner} has no clean way to preserve a safelisted element's raw
 * (non-HTML-escaped) text content, which a {@code <style>} block's own CSS needs; the per-element
 * {@code style} attribute (already allowed) covers the common single-element-styling case
 * instead.
 *
 * <p>Also allows a bare {@code <input type="checkbox">}, restricted to just the
 * {@code type}/{@code checked}/{@code disabled} attributes — added specifically so
 * {@code MarkdownPreviewOperation}'s own GFM task-list-items extension (which renders a task list
 * as a checkbox per {@code <li>}) survives sanitization with its checkboxes still visible, rather
 * than silently disappearing the way a stripped tag otherwise would. Not a real XSS surface: no
 * {@code <form>} tag is safelisted (so a checkbox can never submit anywhere), and none of the 3
 * allowed attributes can carry a URL/script (unlike {@code name}/{@code value}/{@code formaction},
 * deliberately not included).
 *
 * <p>Also allows {@code <del>} (no attributes) — {@link Safelist#relaxed()}'s own built-in tag
 * list already has the older {@code <strike>}, but not {@code <del>}, and
 * {@code MarkdownPreviewOperation}'s own GFM strikethrough extension always renders {@code <del>}
 * (per the GFM spec), never {@code <strike>}; a real bug caught by a failing test before this was
 * added — {@code ~~gone~~} sanitized down to plain unstruck text, silently dropping the
 * strikethrough formatting entirely rather than merely reformatting it.
 */
public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "style", "class", "id")
            .addProtocols("img", "src", "data")
            .addTags("input", "del")
            .addAttributes("input", "type", "checked", "disabled");

    private HtmlSanitizer() {
    }

    /**
     * Sanitizes {@code html} down to markup safe to hand to a (still-sandboxed) preview renderer
     * — see this class's own Javadoc for the exact {@link Safelist} and its known limitations.
     * Never throws — jsoup's parser is deliberately lenient and always produces a best-effort DOM.
     *
     * @param html raw, untrusted HTML (or HTML produced from a trusted conversion, e.g. Markdown)
     * @return the sanitized HTML
     */
    public static String sanitize(String html) {
        Document dirty = Jsoup.parseBodyFragment(html);
        Document clean = new Cleaner(SAFELIST).clean(dirty);
        clean.outputSettings().prettyPrint(false);
        return clean.body().html();
    }
}
