package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.dto.StringCaseResponse;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.StringCaseConverter;

/**
 * Converts raw text into every {@link StringCaseResponse} case variant at once — camelCase,
 * PascalCase, snake_case, kebab-case, CONSTANT_CASE, Title Case, and Sentence case — the one
 * operation in this module whose output is genuinely richer than a single string (see
 * {@code dto.DevUtilResponse}'s own Javadoc for why that meant a dedicated response type here
 * rather than being forced into it, and {@code service.DevUtilOperation}'s own Javadoc for the
 * general rule this follows).
 *
 * <p>Delegates entirely to {@link StringCaseConverter} — see that class's own Javadoc for the word-
 * splitting rules. No {@code minify} concept (there's no "compact form" of a case conversion), and
 * no invalid-input failure path — this never throws, so no matching {@code DevUtilsErrorCode}
 * exists for this operation.
 */
@Component
public class StringCaseOperation implements DevUtilOperation {

    /** Never throws — see this class's own Javadoc for why a pure word-split/re-case transform
     * has no invalid-input failure path. */
    public StringCaseResponse execute(String input) {
        return StringCaseConverter.convert(input);
    }
}
