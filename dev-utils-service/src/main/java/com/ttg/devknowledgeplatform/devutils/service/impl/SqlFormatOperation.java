package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.SqlFormatter;

/**
 * Reformats a raw SQL query/script with one clause per line and paren-depth indentation, or, with
 * {@code minify}, a compact form with comments stripped.
 *
 * <p>Delegates entirely to {@link SqlFormatter} — see that class's own Javadoc for the full design
 * (keyword-driven, not a real SQL-grammar parser; deliberately dialect-agnostic, since a general-
 * purpose formatter has no way to know which SQL dialect it was handed). Like {@code CssOperation}/
 * {@code LessOperation}/{@code ScssOperation}/{@code JsOperation}, this never throws — no
 * invalid-input failure path exists here, so no matching {@code DevUtilsErrorCode} exists for this
 * operation.
 */
@Component
public class SqlFormatOperation implements DevUtilOperation {

    /** Never throws — see this class's own Javadoc for why {@link SqlFormatter} has no
     * invalid-input failure path. */
    public String execute(String input, boolean minify) {
        return minify ? SqlFormatter.minify(input) : SqlFormatter.beautify(input);
    }
}
