package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;

/**
 * Reformats ERB (Embedded RuBy) markup — HTML with {@code <% %>}/{@code <%= %>} tags — by
 * delegating to the same jsoup-based approach {@link HtmlBeautifyOperation} uses, with one added
 * step: every {@code <%...%>} tag is extracted and replaced with an opaque placeholder *before*
 * jsoup ever sees the input, then restored verbatim afterward.
 *
 * <p><b>Why this extra step is needed, not just a nicety.</b> jsoup's tokenizer treats a
 * {@code <} not followed by {@code !}, {@code /}, an ASCII letter, or {@code ?} as plain text (per
 * the HTML5 tokenizer spec, which jsoup implements faithfully) — so {@code <%} on its own would
 * already survive parsing as a literal text node rather than erroring. The real problem is what
 * happens next: jsoup HTML-escapes text-node content on serialization (a literal {@code <} becomes
 * {@code &lt;}), which would silently corrupt the ERB tag's own delimiters on the way back out.
 * Extracting each {@code <%...%>} block whole and substituting an opaque placeholder sidesteps this
 * entirely: the original Ruby code — including any {@code <}/{@code >} inside it, e.g.
 * {@code <% if x < y %>} — never reaches jsoup's tokenizer at all, so there's nothing for jsoup to
 * misinterpret or mangle regardless of what the embedded Ruby actually contains.
 *
 * <p><b>The placeholder itself is a random alphanumeric token, not a control character, per a real,
 * caught-by-a-test mistake.</b> Control characters (e.g. STX/ETX) were tried first on the
 * assumption that jsoup only escapes {@code <}/{@code >}/{@code &}/quotes — wrong: jsoup's own
 * {@code Entities} serialization also escapes non-printable control codepoints as numeric character
 * references (a control char came back as {@code &#x2;}, not the original byte), which would have
 * broken the placeholder-matching restore step just as badly as leaving ERB tags unprotected would
 * have. A marker built from ordinary letters/digits (via {@link UUID}, generated fresh per call so
 * it can't collide with anything a previous request happened to produce) is never escaped by jsoup
 * at all — plain alphanumerics are never special to any HTML serializer.
 *
 * <p>Like {@link HtmlBeautifyOperation}, this never throws: jsoup's lenient parser has no
 * invalid-markup failure path, and the ERB-tag extraction is a plain non-greedy regex scan with no
 * failure mode of its own — an unterminated {@code <%} with no matching {@code %>} is simply left
 * as ordinary text, same as any other fragment jsoup would already tolerate.
 */
@Component
public class ErbOperation implements DevUtilOperation {

    // Non-greedy + DOTALL — an ERB tag never nests, so the first %> after an opener always closes
    // it, and DOTALL lets a multi-line tag (e.g. a multi-statement <% ... %> block) match as one.
    private static final Pattern ERB_TAG = Pattern.compile("<%.*?%>", Pattern.DOTALL);

    /** Never throws — see this class's own Javadoc for why neither the ERB-tag extraction nor
     * jsoup's own lenient parsing has an invalid-input failure path. */
    public String execute(String input, boolean minify) {
        // Generated fresh per call, not a fixed constant — makes an adversarial "craft input that
        // already contains the placeholder text" collision effectively impossible, for a
        // negligible cost on this low-traffic, stateless endpoint.
        String markerPrefix = "erb" + UUID.randomUUID().toString().replace("-", "") + "_";

        List<String> tags = new ArrayList<>();
        String protectedInput = protectErbTags(input, tags, markerPrefix);

        Document document = Jsoup.parseBodyFragment(protectedInput);
        document.outputSettings().prettyPrint(!minify).indentAmount(2);
        String result = document.body().html();

        return restoreErbTags(result, tags, markerPrefix);
    }

    private String protectErbTags(String input, List<String> tags, String markerPrefix) {
        Matcher matcher = ERB_TAG.matcher(input);
        StringBuilder out = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            out.append(input, lastEnd, matcher.start());
            out.append(markerPrefix).append(tags.size()).append("_end");
            tags.add(matcher.group());
            lastEnd = matcher.end();
        }
        out.append(input, lastEnd, input.length());
        return out.toString();
    }

    private String restoreErbTags(String rendered, List<String> tags, String markerPrefix) {
        String result = rendered;
        for (int i = 0; i < tags.size(); i++) {
            String marker = markerPrefix + i + "_end";
            result = result.replace(marker, tags.get(i));
        }
        return result;
    }
}
