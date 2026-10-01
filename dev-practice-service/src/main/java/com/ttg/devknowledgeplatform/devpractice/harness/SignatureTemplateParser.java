package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Reads a problem's method signature out of a code template an admin pastes — e.g.
 * {@code class Solution { public int evalRPN(String[] tokens) {} }} gives {@code evalRPN},
 * {@code INT}, {@code [tokens: STRING_ARRAY]}. One implementation per {@link ProgrammingLanguage}
 * (Strategy), selected by {@link SignatureTemplateParserRegistry} — the inverse of what a
 * {@link LanguageHarness} does when it renders starter code from a signature.
 *
 * <p>Type spellings are never hand-mapped here: every implementation resolves them through a
 * {@link TypeDeclarationIndex} built from that language's own {@link TypeRenderer}, so parsing and
 * rendering share one source of truth and can't drift apart.
 */
public interface SignatureTemplateParser {

    /** The language this parser reads. */
    ProgrammingLanguage language();

    /**
     * @param code the template's source text
     * @return the signature it declares
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException
     *         {@code PROBLEM_TEMPLATE_INVALID}, with a message saying what to fix, if the template
     *         has no (or more than one) method, a missing type annotation, or a type outside the
     *         supported {@code ParamType} vocabulary
     */
    ParsedSignature parse(String code);
}
