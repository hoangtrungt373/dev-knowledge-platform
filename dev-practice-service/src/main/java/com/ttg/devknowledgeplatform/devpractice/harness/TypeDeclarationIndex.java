package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * The inverse of one language's {@link TypeRenderer}: type spelling → {@link ParamType}(s). Built by
 * rendering every {@code ParamType} and grouping by the resulting declaration, so a renderer that
 * maps two types to one spelling (Python's {@code int} for both {@code INT} and {@code LONG}) shows
 * up here as an ambiguous spelling rather than a silent guess.
 *
 * <p>Both the renderer's spellings and the template's are passed through the same
 * {@code normalizer}, so e.g. {@code List[ int ]} and {@code list[int]} both find {@code List[int]}.
 */
final class TypeDeclarationIndex {

    private final Map<String, List<ParamType>> typesBySpelling = new LinkedHashMap<>();
    private final UnaryOperator<String> normalizer;

    /**
     * @param renderer   the language's renderer to invert
     * @param normalizer canonicalizes a spelling (whitespace, aliases) before lookup
     */
    TypeDeclarationIndex(TypeRenderer renderer, UnaryOperator<String> normalizer) {
        this.normalizer = normalizer;
        // values() is declaration order, so each list's first entry is the most common choice
        // (INT before LONG/DOUBLE, INT_ARRAY before DOUBLE_ARRAY).
        for (ParamType type : ParamType.values()) {
            typesBySpelling.computeIfAbsent(normalizer.apply(renderer.render(type).declaration()), k -> new ArrayList<>())
                    .add(type);
        }
    }

    /**
     * @param spelling the type as written in the template
     * @param what     which part of the signature this is, for the error message ("return type",
     *                 "parameter 'tokens'")
     * @return the chosen type and any alternatives
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException {@code PROBLEM_TEMPLATE_INVALID} for an unsupported type
     */
    ParsedSignature.TypeChoice resolve(String spelling, String what) {
        List<ParamType> candidates = typesBySpelling.get(normalizer.apply(spelling));
        if (candidates == null) {
            throw TemplateParsing.invalid("unsupported type '" + spelling.strip() + "' for the " + what
                    + " — supported: " + String.join(", ", typesBySpelling.keySet()));
        }
        return new ParsedSignature.TypeChoice(candidates.get(0), List.copyOf(candidates.subList(1, candidates.size())));
    }
}
