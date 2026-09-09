package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Decodes {@link HtmlEntityEncodeOperation}'s own five character references (
 * {@code &amp; &lt; &gt; &quot; &#39;}) back into their literal characters — plus {@code &apos;}
 * and {@code &#x27;}, the two other conventional spellings for an escaped apostrophe this operation
 * tolerates on decode even though its own encode counterpart only ever emits {@code &#39;}. No
 * broader named-entity table (e.g. {@code &copy;}, {@code &nbsp;}) is recognized — the same
 * "encode/decode are exact inverses of this one fixed set, not a general HTML-entity table"
 * scoping {@link HtmlEntityEncodeOperation}'s own Javadoc explains; any other {@code &...;}
 * sequence passes through completely untouched, exactly as a browser leaves an unrecognized entity
 * reference alone rather than guessing.
 *
 * <p>Decoding is a single left-to-right scan (one {@link Pattern#matcher} pass, not a sequence of
 * {@code String#replace} calls), so a doubly-escaped input like {@code "&amp;lt;"} decodes exactly
 * one level — to {@code "&lt;"}, not {@code "<"} — matching how a browser's own entity decoding
 * never recurses into text a previous decode step just produced.
 *
 * <p>Never throws — every string, including one with no recognizable entities at all, has a valid
 * (possibly unchanged) decoded form, so no matching {@code DevUtilsErrorCode} exists for this
 * operation.
 */
@Component
public class HtmlEntityDecodeOperation implements DevUtilOperation {

    private static final Pattern ENTITY_PATTERN = Pattern.compile("&(amp|lt|gt|quot|apos|#39|#x27);");

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /**
     * Never throws — see this class's own Javadoc for why every string has a valid decoded form.
     */
    public String execute(String input) {
        return ENTITY_PATTERN.matcher(input).replaceAll(match -> switch (match.group(1)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos", "#39", "#x27" -> "'";
            default -> match.group();
        });
    }
}
