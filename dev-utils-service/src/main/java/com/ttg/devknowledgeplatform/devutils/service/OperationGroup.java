package com.ttg.devknowledgeplatform.devutils.service;

/**
 * The broad category a {@link DevUtilOperation} belongs to — shown in the GUI's tool sidebar as a
 * section headline above every operation that shares it, so an admin can tell "this is a text
 * reformatter" from "this is an encoder" from "this is an inspector" at a glance, without having
 * to read each operation's own description.
 *
 * <p>Most operations in this module are {@link #FORMATTERS} (JSON/YAML/HTML/CSS/LESS/SCSS/JS/ERB/
 * XML beautify+minify, JSON↔CSV, SQL format, PHP↔JSON, String Case Converter, Date/Time↔Unix —
 * every one of them either reformats text in place or converts between two closely related
 * formats). The Unix Time Converter pair ({@code DateTimeToUnixOperation}/
 * {@code UnixToDateTimeOperation}) landed here rather than {@link #ENCODERS_DECODERS} on
 * purpose, even though it's structurally a bidirectional pair like that group's own Base64/URL/
 * hex operations: "encode"/"decode" doesn't read naturally for "convert a date into a timestamp"
 * the way it does for those, so it instead follows {@code PhpToJsonOperation}/
 * {@code JsonToPhpOperation}'s own descriptively-named-pair precedent within this group.
 * {@link #ENCODERS_DECODERS} covers every operation that changes a value's own representation
 * rather than just its whitespace/casing — Base64/URL/HTML-entity encode-decode, PHP's own
 * serialize()/unserialize() wire format, and the Hash Generator (a one-way "encoding" into a
 * digest, arguably closer to this group than to {@link #INSPECTORS} — moved here from that group
 * per direct request). {@link #INSPECTORS} covers every operation that *reads* structure out of a
 * value rather than transforming it — a JWT decoder, a regular-expression tester, a URL parser
 * (the last two moved here per direct request; {@link OperationGroup} originally named "a future
 * JWT decoder" as this group's own worked example, before any of the three actually existed).
 * {@link #WEB} and {@link #GENERATORS} were originally declared ahead of the operations that would
 * eventually use them, so the GUI's own group-by-{@link #getLabel()} sidebar rendering already had
 * a stable, complete set of sections to iterate whenever the first one landed — both now do.
 * {@link #WEB} covers {@code HtmlPreviewOperation}/{@code MarkdownPreviewOperation} (a live,
 * sandboxed-preview rendering of HTML/Markdown), {@code HtmlToTsxOperation} (HTML → JSX-flavored
 * markup), {@code ColorConverterOperation} (a color into every common representation at once), and
 * {@code SvgToCssOperation} (SVG markup into a CSS {@code background-image} data URI rule) — a URL
 * parser was considered for this group too, given the same "inherently web-specific" reasoning,
 * but landed in {@link #INSPECTORS} instead per direct request, since reading structure out of a
 * value is the more specific/decisive shape here. {@link #GENERATORS} covers
 * {@code LoremIpsumGeneratorOperation} (1-20 paragraphs of classic placeholder text) — this
 * group's own original worked example, landed for real; a client-side-only QR Code
 * Reader/Generator also lives in this group on the GUI side, but has no backend operation class at
 * all (see {@code gui/CLAUDE.md}'s own dev-utils section).
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
