package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

/**
 * A {@link DefaultPrettyPrinter} fixing four ways Jackson's own default pretty-print style
 * diverges from the JSON formatting every mainstream tool (VS Code's own formatter, most online
 * "JSON beautifier" sites, {@code JSON.stringify(value, null, 2)}) actually produces — a real bug
 * caught via a direct before/after comparison against one of those tools, not a style preference:
 *
 * <ol>
 *   <li>{@code writeObjectFieldValueSeparator} — {@link DefaultPrettyPrinter}'s own default writes
 *       {@code " : "} (a space on *both* sides of the colon: {@code "key" : "value"}); this writes
 *       just {@code ": "} (space after only: {@code "key": "value"}).</li>
 *   <li>Arrays — {@link DefaultPrettyPrinter}'s own default only multi-line-indents *objects*; an
 *       array uses {@link com.fasterxml.jackson.core.util.FixedSpaceIndenter}, a single-line,
 *       space-separated style ({@code [ "a", "b", "c" ]}). The constructor below sets the *array*
 *       indenter to the same {@link DefaultIndenter} objects already use, so an array with real
 *       elements renders one element per line, indented, exactly like a nested object's own
 *       fields.</li>
 *   <li>Empty containers — with a multi-line indenter, {@link DefaultPrettyPrinter}'s own
 *       {@code writeEndArray}/{@code writeEndObject} still special-case a zero-entry container by
 *       writing a single literal space before the closing bracket ({@code [ ]}/{@code { }}) rather
 *       than collapsing it to {@code []}/{@code {}} the way every mainstream formatter does. Both
 *       methods are overridden below to drop that one padding-space write.</li>
 *   <li>Line endings — {@link DefaultPrettyPrinter}'s own default indenter,
 *       {@code DefaultIndenter.SYSTEM_LINEFEED_INSTANCE}, uses {@code System.lineSeparator()}: CRLF
 *       on a Windows dev machine, LF wherever this service actually deploys (Linux Docker
 *       containers). That makes output silently platform-dependent — identical input produces a
 *       byte-for-byte different response depending on which OS ran the JVM, caught only by
 *       measuring the constructor's early attempt (system-dependent EOL, array-only fix) byte-for-
 *       byte rather than trusting a test-failure diff, which visually (and misleadingly) looked
 *       like a doubled-indentation bug instead of a CRLF-vs-LF one. {@link #LF_INDENTER} fixes both
 *       indenters to a literal {@code "\n"} instead.</li>
 * </ol>
 *
 * <p>Used by {@link JsonNodeIo#write} in place of {@code ObjectMapper#writerWithDefaultPrettyPrinter()}
 * — every operation that goes through that shared helper (JSON Format, YAML→JSON, CSV→JSON,
 * PHP→JSON) gets the fix at once, rather than needing four separate call-site changes.
 */
public final class ConventionalJsonPrettyPrinter extends DefaultPrettyPrinter {

    private static final long serialVersionUID = 1L;

    // A 4th real bug, caught only once the first fix was actually measured byte-for-byte rather
    // than trusted from a test-failure diff (see this class's own commit/PR history if picking
    // through this): DefaultPrettyPrinter's own default indenter is
    // DefaultIndenter.SYSTEM_LINEFEED_INSTANCE, whose line ending is System.lineSeparator() — CRLF
    // on a Windows dev machine, LF wherever this service actually deploys (Linux Docker
    // containers, per this reactor's own docker-compose setup). That made this class's own output
    // silently platform-dependent, not just a cosmetic style choice: identical input produces a
    // byte-for-byte different response depending on which OS happened to run the JVM. A fixed "\n"
    // is also simply the conventional choice for JSON — nothing consumes CRLF-terminated JSON on
    // purpose.
    private static final DefaultIndenter LF_INDENTER = new DefaultIndenter("  ", "\n");

    public ConventionalJsonPrettyPrinter() {
        super();
        // Both indenters set explicitly to the fixed-EOL instance above — objects, not just
        // arrays, since DefaultPrettyPrinter's own default object indenter has the same
        // system-dependent EOL problem this class exists to avoid.
        indentObjectsWith(LF_INDENTER);
        indentArraysWith(LF_INDENTER);
    }

    /** Copy constructor — required by the {@link DefaultPrettyPrinter} contract so
     * {@link #createInstance()} can hand the generator a fresh, independent copy (this class is
     * stateful, via its inherited nesting-depth counter) that still carries this subclass's own
     * overrides and indenter configuration forward. */
    public ConventionalJsonPrettyPrinter(ConventionalJsonPrettyPrinter base) {
        super(base);
    }

    @Override
    public DefaultPrettyPrinter createInstance() {
        return new ConventionalJsonPrettyPrinter(this);
    }

    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator g) throws IOException {
        g.writeRaw(": ");
    }

    @Override
    public void writeEndArray(JsonGenerator g, int nrOfValues) throws IOException {
        if (!_arrayIndenter.isInline()) {
            --_nesting;
        }
        if (nrOfValues > 0) {
            _arrayIndenter.writeIndentation(g, _nesting);
        }
        g.writeRaw(']');
    }

    @Override
    public void writeEndObject(JsonGenerator g, int nrOfEntries) throws IOException {
        if (!_objectIndenter.isInline()) {
            --_nesting;
        }
        if (nrOfEntries > 0) {
            _objectIndenter.writeIndentation(g, _nesting);
        }
        g.writeRaw('}');
    }
}
