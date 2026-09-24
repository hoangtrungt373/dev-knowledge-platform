package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * Maps every {@link ParamType} to its {@link TypeSyntax} in one language — the single place a
 * language's per-type knowledge lives, so adding a {@code ParamType} means one new line per
 * language instead of edits scattered across several switch statements.
 *
 * <p>Implementations are expected to use an exhaustive {@code switch} expression with no
 * {@code default} branch: that makes the compiler reject any implementation that doesn't handle a
 * newly added {@code ParamType}, which is what keeps {@code ParamType}'s "every harness supports
 * every type" rule enforced even though the program skeletons themselves now live in templates.
 */
public interface TypeRenderer {

    /**
     * @param type a parameter or return type from a problem's method signature
     * @return how that type is spelled in this renderer's language
     */
    TypeSyntax render(ParamType type);
}
