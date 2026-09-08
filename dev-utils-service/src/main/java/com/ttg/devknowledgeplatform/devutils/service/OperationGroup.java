package com.ttg.devknowledgeplatform.devutils.service;

/**
 * The broad category a {@link DevUtilOperation} belongs to — shown in the GUI's tool sidebar as a
 * section headline above every operation that shares it, so an admin can tell "this is a text
 * reformatter" from "this is an encoder" from "this is an inspector" at a glance, without having
 * to read each operation's own description.
 *
 * <p>Every operation in this module today is {@link #FORMATTERS} (JSON/YAML/HTML/CSS/LESS/SCSS/
 * JS/ERB/XML beautify+minify, JSON↔CSV, SQL format, PHP↔JSON, String Case Converter — every one of
 * them either reformats text in place or converts between two closely related text formats). The
 * other four groups are declared ahead of the operations that will actually use them, so the GUI's
 * own group-by-{@link #getLabel()} sidebar rendering has a stable, complete set of sections to
 * iterate from day one rather than needing a second change once the first non-{@code FORMATTERS}
 * operation lands: {@link #ENCODERS_DECODERS} (e.g. a future Base64/URL/HTML-entity encoder-
 * decoder — genuinely different from a formatter, since it changes a value's own representation
 * rather than just its whitespace/casing), {@link #INSPECTORS} (e.g. a future JWT decoder/hash
 * calculator — reads structure out of a value rather than transforming it), {@link #WEB} (e.g. a
 * future HTTP header/user-agent parser — inherently about a web-specific concept, not a generic
 * text shape), {@link #GENERATORS} (e.g. a future UUID/Lorem Ipsum generator — produces new
 * content from little or no input, unlike every operation above, which all transform an existing
 * input).
 */
public enum OperationGroup {
    FORMATTERS("Formatters"),
    ENCODERS_DECODERS("Encoders/Decoders"),
    INSPECTORS("Inspectors"),
    WEB("Web"),
    GENERATORS("Generators");

    private final String label;

    OperationGroup(String label) {
        this.label = label;
    }

    /** The human-readable label the GUI renders as this group's own sidebar headline — plain
     * title case ({@code "Encoders/Decoders"}, not {@code "ENCODERS/DECODERS"}); any visual
     * uppercasing is a GUI-side {@code text-transform}, the same convention already used for the
     * sidebar's own "Tools" caption, not baked into this label itself. */
    public String getLabel() {
        return label;
    }
}
