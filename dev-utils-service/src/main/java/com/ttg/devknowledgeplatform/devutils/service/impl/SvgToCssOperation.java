package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Converts raw SVG markup into a CSS rule that renders it as a {@code background-image}, via a
 * percent-encoded (not base64) {@code data:image/svg+xml,...} URI — base64 would need no escaping
 * but costs ~33% more bytes than the source text, while percent-encoding only inflates the
 * characters that actually need it (every letter/digit and {@code - _ . * } stays literal); this
 * is the same "percent-encode, don't base64, an inline SVG" trade-off most standalone
 * "SVG in CSS" tools make. The fifth {@link OperationGroup#WEB} operation.
 *
 * <p>The percent-encoding itself reuses {@link UrlEncodeOperation}'s own exact technique —
 * {@link URLEncoder#encode(String, java.nio.charset.Charset)} (UTF-8) with its one divergence from
 * conventional URI percent-encoding corrected ({@code +} for space, rewritten to {@code %20}) —
 * confirmed against the reported example byte-for-byte before relying on it. <b>Known, deliberate
 * gap shared with that same operation, not chased further here either</b>: {@code URLEncoder}
 * additionally percent-encodes 4 characters JavaScript's own {@code encodeURIComponent} (what most
 * other "SVG to CSS" tools use) leaves literal — {@code ! ~ ' (} and {@code )} — so an SVG
 * containing one of those (e.g. a {@code transform="matrix(...)"} attribute) encodes slightly
 * larger here than it would elsewhere; still a correct, valid data URI regardless, just not the
 * minimum possible byte count for that specific character set.
 *
 * <p>Uses a <b>fixed, generic {@code .icon} selector name</b> (per direct request, the same
 * "simpler than an extra input field" choice already made for {@code ColorConverterOperation}'s
 * own {@code --color} CSS variable name) — the caller renames it themselves when pasting. Also
 * fixed: {@code background-repeat: no-repeat}/{@code background-position: center}/
 * {@code background-size: contain}, the same 3 companion declarations every hand-written
 * "SVG background icon" CSS rule needs regardless of the SVG's own content, so this operation
 * always includes them rather than making them conditional on something it can't actually detect.
 *
 * <p><b>Deliberately does not minify/collapse the SVG's own internal whitespace before
 * encoding</b> — unlike {@code minify}, which only controls this operation's own *CSS* output
 * (one line vs. indented), not the SVG source text embedded inside the data URI; a multi-line,
 * indented SVG pasted in encodes every one of its own newlines/indentation spaces verbatim (each
 * as {@code %0A}/{@code %20}), producing a longer data URI than a pre-compacted SVG would. Keeps
 * this operation a pure percent-encoding step, the same narrow scope
 * {@code UrlEncodeOperation} itself has — paste already-compact SVG for the most compact result.
 *
 * <p>Never throws — like {@code UrlEncodeOperation}/{@code Base64EncodeOperation}, every string
 * has a valid percent-encoding, and this operation makes no attempt to validate that
 * {@code input} is actually well-formed SVG/XML at all (its whole job is encoding whatever text
 * it's given, not verifying it).
 */
@Component
public class SvgToCssOperation implements DevUtilOperation {

    private static final String CSS_CLASS_NAME = "icon";

    @Override
    public OperationGroup group() {
        return OperationGroup.WEB;
    }

    /**
     * Converts {@code input} (raw SVG markup) into a {@code .icon { ... }} CSS rule using it as a
     * percent-encoded {@code background-image} data URI — pretty-printed (one declaration per
     * line, 2-space indent) or, with {@code minify}, a single compact line. Never throws.
     *
     * @param input  raw SVG markup, e.g. {@code <svg xmlns="..." ...>...</svg>}
     * @param minify {@code false} for indented, multi-line CSS; {@code true} for a single line
     * @return the CSS rule
     */
    public String execute(String input, boolean minify) {
        String dataUri = "data:image/svg+xml," + encode(input == null ? "" : input.trim());
        if (minify) {
            return "." + CSS_CLASS_NAME + "{background-image:url(\"" + dataUri
                    + "\");background-repeat:no-repeat;background-position:center;background-size:contain;}";
        }
        return """
                .%s {
                  background-image: url("%s");
                  background-repeat: no-repeat;
                  background-position: center;
                  background-size: contain;
                }""".formatted(CSS_CLASS_NAME, dataUri);
    }

    private static String encode(String svg) {
        return URLEncoder.encode(svg, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
