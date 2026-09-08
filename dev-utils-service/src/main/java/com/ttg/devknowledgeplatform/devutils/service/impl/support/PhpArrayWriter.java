package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Renders a Jackson {@link JsonNode} tree as a PHP array literal — the JSON→PHP counterpart to
 * {@link PhpArrayParser}'s PHP→JSON direction — wrapped in a {@code <?php return ...;} snippet
 * that {@link PhpArrayParser#parse} can read straight back in. Used by {@code JsonToPhpOperation}.
 *
 * <p>A JSON object becomes a PHP associative array (every field name written as a single-quoted
 * string key, since a JSON object's keys are always strings); a JSON array becomes a plain PHP
 * list literal with no explicit keys. {@link #write} produces one clause per line, 2-space
 * indented (matching this module's existing convention); {@link #write}'s {@code minify} mode
 * collapses everything onto one line with no space around {@code =>} or after a comma, the same
 * "minimal necessary whitespace" style {@code JsonFormatOperation}'s own compact writer already
 * uses.
 */
public final class PhpArrayWriter {

    private static final int INDENT_WIDTH = 2;

    private PhpArrayWriter() {
    }

    public static String write(JsonNode root, boolean minify) {
        StringBuilder out = new StringBuilder();
        if (minify) {
            out.append("<?php return ");
            writeValue(root, out, 0, true);
            out.append(';');
        } else {
            // A blank line between "<?php" and "return" — a real bug, reported directly against a
            // real payload: the standard convention this snippet's own shape is meant to evoke
            // (most PHP file/snippet generators, and PSR-12-influenced style guides, leave a blank
            // line after the opening tag before any real statement) always has one; this was
            // missing it entirely. minify's own single-line output is untouched — there's no
            // "blank line" concept once everything collapses onto one line anyway.
            out.append("<?php\n\nreturn ");
            writeValue(root, out, 0, false);
            out.append(';');
        }
        return out.toString();
    }

    private static void writeValue(JsonNode node, StringBuilder out, int depth, boolean minify) {
        if (node.isObject()) {
            writeContainer(node.fields(), out, depth, minify, true);
        } else if (node.isArray()) {
            writeContainer(arrayEntryIterator(node), out, depth, minify, false);
        } else if (node.isTextual()) {
            out.append('\'').append(escapeSingleQuoted(node.textValue())).append('\'');
        } else {
            // NUMBER/BOOLEAN/NULL — Jackson's own asText() already renders exactly the PHP-literal
            // spelling for all three ("128", "1.5", "true", "false", "null"), so there's nothing
            // format-specific left to do beyond writing it verbatim.
            out.append(node.asText());
        }
    }

    private static void writeContainer(
            Iterator<Map.Entry<String, JsonNode>> entries, StringBuilder out, int depth, boolean minify,
            boolean withKeys) {
        out.append('[');
        if (!entries.hasNext()) {
            out.append(']');
            return;
        }
        if (!minify) {
            out.append('\n');
        }
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            if (!minify) {
                out.append(" ".repeat((depth + 1) * INDENT_WIDTH));
            }
            if (withKeys) {
                out.append('\'').append(escapeSingleQuoted(entry.getKey())).append('\'').append(minify ? "=>" : " => ");
            }
            writeValue(entry.getValue(), out, depth + 1, minify);
            if (entries.hasNext()) {
                out.append(minify ? "," : ",\n");
            } else if (!minify) {
                out.append('\n');
            }
        }
        if (!minify) {
            out.append(" ".repeat(depth * INDENT_WIDTH));
        }
        out.append(']');
    }

    private static Iterator<Map.Entry<String, JsonNode>> arrayEntryIterator(JsonNode arrayNode) {
        // Reuses the same `Map.Entry`-based container-writer as an object, keyed by each element's
        // own index — the index is only ever consulted when `withKeys` is true (object mode), so
        // for a plain array it's discarded, but building one iterator this way avoids a second,
        // near-duplicate writeContainer overload just for the no-keys case.
        return new Iterator<>() {
            private int index = 0;
            private final Iterator<JsonNode> elements = arrayNode.elements();

            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public Map.Entry<String, JsonNode> next() {
                return Map.entry(String.valueOf(index++), elements.next());
            }
        };
    }

    private static String escapeSingleQuoted(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
