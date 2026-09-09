package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Escapes the five characters that are structurally significant in HTML markup —
 * {@code & < > " '} — into their named character references ({@code &amp; &lt; &gt; &quot;
 * &#39;}), the same fixed set XML 1.0/1.1's own predefined entities cover. Deliberately does
 * <b>not</b> escape anything else, including non-ASCII text (e.g. {@code ©}) — this is a markup
 * "make this text safe to embed literally inside an HTML document" transform, not a full
 * ISO-8859-1/HTML4 named-entity table encoder (which would also rewrite {@code ©} to
 * {@code &copy;} and every other Latin-1 character with its own entity) — a genuinely bigger,
 * different tool this operation intentionally isn't. {@code &} is handled first (as its own
 * single character-by-character pass, not a sequence of {@code String#replace} calls), so a
 * literal {@code &} already present in the input is escaped exactly once, never re-escaped as part
 * of escaping a later character's own entity.
 *
 * <p>Never throws — every string has a valid escaped form, so no matching
 * {@code DevUtilsErrorCode} exists for this operation (the same reasoning
 * {@link Base64EncodeOperation}/{@link UrlEncodeOperation}'s own Javadoc gives for themselves).
 */
@Component
public class HtmlEntityEncodeOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /** Never throws — see this class's own Javadoc for why every string has a valid escaped form. */
    public String execute(String input) {
        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                case '\'' -> result.append("&#39;");
                default -> result.append(c);
            }
        }
        return result.toString();
    }
}
